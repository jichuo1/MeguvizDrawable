#!/usr/bin/env python3
"""Dump the public/protected Java ABI of one built JAR or AAR; no third-party dependencies."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, count):
        end = self.pos + count
        if end > len(self.data):
            raise ValueError("truncated class file")
        result = self.data[self.pos:end]
        self.pos = end
        return result

    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack(">H", self.take(2))[0]
    def u4(self): return struct.unpack(">I", self.take(4))[0]


def class_visibility(data):
    """Return (own visibility, outer binary name, is member) from class metadata."""
    r = Reader(data)
    if r.u4() != 0xCAFEBABE:
        raise ValueError("invalid class magic")
    r.take(4)
    count = r.u2()
    pool = [None] * count
    i = 1
    while i < count:
        tag = r.u1()
        if tag == 1:
            pool[i] = r.take(r.u2()).decode("utf-8", errors="replace")
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, r.u2())
        elif tag in (3, 4):
            r.take(4)
        elif tag in (5, 6):
            r.take(8)
            i += 1
        elif tag in (9, 10, 11, 12, 17, 18):
            r.take(4)
        elif tag == 15:
            r.take(3)
        else:
            raise ValueError("unsupported constant pool tag " + str(tag))
        i += 1
    access, this_class = r.u2(), r.u2()
    r.u2()
    r.take(2 * r.u2())
    for _ in range(2):  # fields and methods
        for _ in range(r.u2()):
            r.take(6)
            for _ in range(r.u2()):
                r.u2()
                r.take(r.u4())
    outer = None
    member = False
    for _ in range(r.u2()):
        name = pool[r.u2()]
        payload = Reader(r.take(r.u4()))
        if name == "InnerClasses":
            for _ in range(payload.u2()):
                inner, outer_index, inner_name, flags = payload.u2(), payload.u2(), payload.u2(), payload.u2()
                if inner == this_class:
                    member = True
                    access = flags
                    if outer_index and inner_name:
                        entry = pool[outer_index]
                        if not isinstance(entry, tuple) or entry[0] != 7:
                            raise ValueError("invalid outer class reference")
                        outer = pool[entry[1]].replace("/", ".")
    visibility = "public" if access & 1 else "protected" if access & 4 else "hidden"
    return visibility, outer, member


def jar_classes(data, label, classes):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            path = entry.filename
            if not path.endswith(".class") or path.endswith("module-info.class"):
                continue
            if path.startswith("META-INF/versions/"):
                raise ValueError("multi-release JARs require an explicit version policy")
            name = path[:-6].replace("/", ".")
            if name in classes:
                raise ValueError("duplicate class across artifact: " + name)
            classes[name] = (archive.read(entry), label)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path, help="built .jar or .aar")
    parser.add_argument("--output", type=Path, required=True, help="API text output")
    parser.add_argument("--metadata", type=Path, required=True, help="SHA-256 and tool receipt")
    parser.add_argument("--javap", default=shutil.which("javap"), help="JDK javap executable")
    args = parser.parse_args()
    if not args.javap:
        parser.error("javap not found; provide --javap from a JDK")
    if args.output.resolve() == args.metadata.resolve() or args.artifact.resolve() in (args.output.resolve(), args.metadata.resolve()):
        parser.error("artifact and output paths must be distinct")
    raw = args.artifact.read_bytes()
    suffix = args.artifact.suffix.lower()
    jars = []
    if suffix == ".jar":
        jars.append(("artifact.jar", raw))
    elif suffix == ".aar":
        with zipfile.ZipFile(io.BytesIO(raw)) as aar:
            names = aar.namelist()
            if names.count("classes.jar") != 1:
                raise ValueError("AAR must contain exactly one classes.jar")
            jars.append(("classes.jar", aar.read("classes.jar")))
            for name in sorted(n for n in names if n.startswith("libs/") and n.endswith(".jar")):
                if names.count(name) != 1:
                    raise ValueError("duplicate embedded JAR: " + name)
                jars.append((name, aar.read(name)))
    else:
        parser.error("artifact must end in .jar or .aar")
    classes = {}
    for name, data in jars:
        jar_classes(data, name, classes)
    if not classes:
        raise ValueError("artifact has no class files")
    info = {name: class_visibility(data) for name, (data, _) in classes.items()}

    def visible(name, stack=()):
        if name in stack:
            raise ValueError("cyclic enclosing classes: " + name)
        if name not in info:
            raise ValueError("enclosing class missing from artifact: " + name)
        access, outer, member = info[name]
        if access == "hidden" or (member and outer is None):
            return False
        return visible(outer, stack + (name,)) if outer else True

    exported = sorted(name for name in classes if visible(name))
    if not exported:
        raise ValueError("artifact exposes no public or protected classes")
    with tempfile.TemporaryDirectory(prefix="meguviz-abi-") as tmp:
        paths = []
        for index, (_, data) in enumerate(jars):
            path = Path(tmp) / (str(index) + ".jar")
            path.write_bytes(data)
            paths.append(str(path))
        cp = os.pathsep.join(paths)
        sections = []
        for name in exported:
            run = subprocess.run([args.javap, "-classpath", cp, "-protected", "-s", "-constants", name],
                                 capture_output=True, text=True, encoding="utf-8", errors="replace", check=True)
            lines = [line.rstrip() for line in run.stdout.splitlines() if not line.startswith("Compiled from ")]
            sections.append("# " + name + " (" + info[name][0] + ")\n" + "\n".join(lines).strip())
        api = "\n\n".join(sections) + "\n"
    version = subprocess.run([args.javap, "-version"], capture_output=True, text=True, check=True).stdout.strip()
    receipt = {"artifact": args.artifact.name, "artifact_sha256": digest(raw),
               "embedded_jars": [{"name": n, "sha256": digest(d)} for n, d in jars],
               "api_sha256": digest(api.encode("utf-8")), "classes": len(exported),
               "javap_version": version, "status": "initial_baseline_no_compatibility_claim"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(api, encoding="utf-8", newline="\n")
    args.metadata.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    print("exported classes:", len(exported), "artifact sha256:", receipt["artifact_sha256"])


if __name__ == "__main__":
    main()
