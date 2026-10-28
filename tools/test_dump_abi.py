#!/usr/bin/env python3
"""Exercise nested visibility and deterministic JAR/AAR ABI extraction."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile


def main():
    script = Path(__file__).with_name("dump_abi.py").resolve()
    with tempfile.TemporaryDirectory(prefix="meguviz-abi-test-") as directory:
        root = Path(directory)
        source = root / "Outer.java"
        source.write_text("""public class Outer {
    public static class Pub { public int value; protected void work() {} private void secret() {} }
    protected static class Pro { public void method() {} }
    static class PackageOnly { public void method() {} }
    private static class Hidden { public static class Nested {} }
}
""", encoding="utf-8")
        subprocess.run(["javac", "--release", "8", "-d", str(root), str(source)], check=True)
        jar = root / "fixture.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for cls in sorted(root.glob("*.class")):
                archive.write(cls, cls.name)
        aar = root / "fixture.aar"
        with zipfile.ZipFile(aar, "w") as archive:
            archive.writestr("classes.jar", jar.read_bytes())
        outputs = []
        for i, artifact in enumerate((jar, jar, aar)):
            api, receipt = root / (str(i) + ".txt"), root / (str(i) + ".json")
            subprocess.run([sys.executable, str(script), str(artifact), "--output", str(api),
                            "--metadata", str(receipt)], check=True)
            text = api.read_text(encoding="utf-8")
            assert "# Outer (public)" in text
            assert "# Outer$Pub (public)" in text
            assert "# Outer$Pro (protected)" in text
            assert "Outer$Hidden" not in text and "Outer$PackageOnly" not in text
            assert "protected void work()" in text and "secret()" not in text
            assert json.loads(receipt.read_text(encoding="utf-8"))["classes"] == 3
            outputs.append(text)
        assert outputs[0] == outputs[1] == outputs[2]
    print("ABI fixture: PASS (nested visibility, member scope, deterministic JAR/AAR)")


if __name__ == "__main__":
    main()
