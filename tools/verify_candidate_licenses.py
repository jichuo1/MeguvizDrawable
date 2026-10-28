#!/usr/bin/env python3
"""Verify that each local candidate archive carries the repository license."""

import argparse
import hashlib
from pathlib import Path
import sys
import zipfile


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=root / "build" / "staging-maven")
    parser.add_argument("--version", default="0.1.0-dev.3")
    args = parser.parse_args()
    group_path = args.repository / "io" / "github" / "jichuo1" / "meguviz"
    version = args.version
    artifacts = (
        ("meguviz-drawable-core", f"meguviz-drawable-core-{version}.jar"),
        ("meguviz-drawable-core", f"meguviz-drawable-core-{version}-sources.jar"),
        ("meguviz-drawable-android", f"meguviz-drawable-android-{version}.aar"),
        ("meguviz-drawable-android", f"meguviz-drawable-android-{version}-sources.jar"),
    )
    try:
        license_bytes = (root / "LICENSE").read_bytes()
        for module, filename in artifacts:
            path = group_path / module / version / filename
            with zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                if names.count("META-INF/LICENSE") != 1:
                    raise ValueError(f"{filename}: expected exactly one META-INF/LICENSE")
                if archive.read("META-INF/LICENSE") != license_bytes:
                    raise ValueError(f"{filename}: bundled LICENSE differs from repository LICENSE")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            print(f"PASS {filename} sha256={digest}")
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
