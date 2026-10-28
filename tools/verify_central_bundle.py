#!/usr/bin/env python3
"""Check a Maven Central Portal upload bundle before uploading it.

Verifies the Central publishing requirements that can be checked offline:
every artifact has a detached .asc signature plus matching .md5 and .sha1
checksums; each module ships main artifact, POM, sources and javadoc jars;
the POM carries name, description, url, license, developer and scm; versions
are consistent and not SNAPSHOT. Signatures are verified with the system gpg
against its keyring unless --skip-gpg is given. It cannot check that the
public key is published on a key server or that the namespace is verified.

Usage: python -I tools/verify_central_bundle.py BUNDLE.zip [--skip-gpg]
"""
import hashlib
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import PurePosixPath

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
CHECKSUM_SUFFIXES = (".md5", ".sha1", ".sha256", ".sha512")


def main(argv):
    if len(argv) not in (2, 3) or (len(argv) == 3 and argv[2] != "--skip-gpg"):
        print(__doc__)
        return 2
    skip_gpg = len(argv) == 3
    failures = []
    with zipfile.ZipFile(argv[1]) as bundle:
        names = [n for n in bundle.namelist() if not n.endswith("/")]
        modules = {}
        for name in names:
            parts = PurePosixPath(name).parts
            if parts[-1].startswith("maven-metadata.xml"):
                failures.append("remove %s: the Portal generates repository metadata itself" % name)
                continue
            if len(parts) < 3:
                failures.append("file outside a module directory: " + name)
                continue
            modules.setdefault(parts[:-1], []).append(parts[-1])
        if not modules:
            failures.append("bundle has no modules")
        for directory, files in sorted(modules.items()):
            check_module(bundle, directory, files, skip_gpg, failures)
    if failures:
        print("FAIL")
        for failure in failures:
            print(" -", failure)
        return 1
    print("PASS: %d modules, %d files" % (len(modules), sum(len(f) for f in modules.values())))
    return 0


def check_module(bundle, directory, files, skip_gpg, failures):
    version = directory[-1]
    artifact = directory[-2]
    label = "/".join(directory)
    if version.endswith("SNAPSHOT"):
        failures.append(label + ": SNAPSHOT versions cannot be released")
    prefix = "%s-%s" % (artifact, version)
    payload = [f for f in files if not f.endswith(CHECKSUM_SUFFIXES) and not f.endswith(".asc")]
    for f in payload:
        if not f.startswith(prefix):
            failures.append("%s: unexpected file name %s" % (label, f))
    main_ext = ".aar" if (prefix + ".aar") in payload else ".jar"
    for required in (prefix + main_ext, prefix + ".pom", prefix + "-sources.jar", prefix + "-javadoc.jar"):
        if required not in payload:
            failures.append("%s: missing %s" % (label, required))
    for f in payload:
        data = bundle.read("/".join(directory + (f,)))
        for suffix, algorithm in ((".md5", "md5"), (".sha1", "sha1")):
            expected = read_text(bundle, directory, f + suffix)
            if expected is None:
                failures.append("%s: missing %s%s" % (label, f, suffix))
            elif expected.split()[0].lower() != hashlib.new(algorithm, data).hexdigest():
                failures.append("%s: %s%s does not match" % (label, f, suffix))
        signature = "/".join(directory + (f + ".asc",))
        if signature not in bundle.namelist():
            failures.append("%s: missing %s.asc" % (label, f))
        elif not skip_gpg:
            verify_signature(data, bundle.read(signature), label + "/" + f, failures)
    pom = prefix + ".pom"
    if pom in payload:
        check_pom(bundle.read("/".join(directory + (pom,))), label, version, failures)


def read_text(bundle, directory, name):
    path = "/".join(directory + (name,))
    return bundle.read(path).decode("ascii", "replace") if path in bundle.namelist() else None


def verify_signature(data, signature, label, failures):
    with tempfile.TemporaryDirectory() as tmp:
        data_path = tmp + "/data"
        sig_path = tmp + "/data.asc"
        open(data_path, "wb").write(data)
        open(sig_path, "wb").write(signature)
        result = subprocess.run(["gpg", "--batch", "--verify", sig_path, data_path],
                                capture_output=True, text=True)
    if result.returncode != 0:
        failures.append("%s: signature does not verify (%s)" % (label, result.stderr.strip().splitlines()[-1:]))


def check_pom(data, label, version, failures):
    root = ET.fromstring(data)

    def text(path):
        node = root.find(path, NS)
        return node.text.strip() if node is not None and node.text and node.text.strip() else None

    for field, path in (("name", "m:name"), ("description", "m:description"), ("url", "m:url"),
                        ("license name", "m:licenses/m:license/m:name"),
                        ("license url", "m:licenses/m:license/m:url"),
                        ("developer", "m:developers/m:developer/m:name"),
                        ("scm url", "m:scm/m:url"), ("scm connection", "m:scm/m:connection"),
                        ("groupId", "m:groupId"), ("artifactId", "m:artifactId"), ("version", "m:version")):
        if text(path) is None:
            failures.append("%s: POM lacks %s" % (label, field))
    if text("m:version") not in (None, version):
        failures.append("%s: POM version %s differs from directory" % (label, text("m:version")))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
