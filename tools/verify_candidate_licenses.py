#!/usr/bin/env python3
"""Verify that each local candidate archive carries the repository license.

Every module is versioned on its own (a module that was already released keeps its version while
another one moves on), so the version of each is read from its build.gradle.kts.
"""

import argparse
import hashlib
from pathlib import Path
import re
import sys
import zipfile

# (module directory, artifactId, main archive extension)
MODULES = (
    ("core", "meguviz-drawable-core", "jar"),
    ("android", "meguviz-drawable-android", "aar"),
    ("compose", "meguviz-drawable-compose", "aar"),
    ("meguviz-android", "meguviz-drawable-meguviz-android", "aar"),
    ("meguviz-ui", "meguviz-drawable-meguviz-ui", "aar"),
    ("lumen", "meguviz-drawable-lumen", "aar"),
)


def module_version(root: Path, directory: str) -> str:
    text = (root / directory / "build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'^version = "([^"]+)"', text, re.MULTILINE)
    if match is None:
        raise ValueError(f"{directory}/build.gradle.kts declares no version")
    return match.group(1)


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=root / "build" / "staging-maven")
    args = parser.parse_args()
    group_path = args.repository / "io" / "github" / "jichuo1" / "meguviz"
    try:
        license_bytes = (root / "LICENSE").read_bytes()
        checked = 0
        for directory, artifact_id, extension in MODULES:
            if not (root / directory / "build.gradle.kts").is_file():
                continue
            version = module_version(root, directory)
            module_path = group_path / artifact_id / version
            if not module_path.is_dir():
                continue
            for filename in (f"{artifact_id}-{version}.{extension}", f"{artifact_id}-{version}-sources.jar"):
                path = module_path / filename
                with zipfile.ZipFile(path) as archive:
                    names = archive.namelist()
                    if names.count("META-INF/LICENSE") != 1:
                        raise ValueError(f"{filename}: expected exactly one META-INF/LICENSE")
                    if archive.read("META-INF/LICENSE") != license_bytes:
                        raise ValueError(f"{filename}: bundled LICENSE differs from repository LICENSE")
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                print(f"PASS {filename} sha256={digest}")
                checked += 1
        if checked == 0:
            raise ValueError("no staged candidate found; publish to the staging repository first")
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
