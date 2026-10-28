# Candidate ABI baseline

`tools/dump_abi.py` reads a built JAR or AAR, including its `classes.jar` and
embedded `libs/*.jar`, and records public/protected classes and members with
JVM descriptors and constant values. It includes visible nested types and
checks the visibility of their enclosing types. Duplicate classes and
multi-release JARs are rejected rather than silently choosing a definition.

Use Python 3.10 or newer and JDK 17 with `javap` on PATH. Generate from the exact staged
artifacts; example for PowerShell:

```powershell
python tools/dump_abi.py build/staging-maven/io/github/jichuo1/meguviz/meguviz-drawable-core/0.1.0-dev.1/meguviz-drawable-core-0.1.0-dev.1.jar --output docs/abi/0.1.0-dev.1/core-api.txt --metadata docs/abi/0.1.0-dev.1/core-api.json
python tools/dump_abi.py build/staging-maven/io/github/jichuo1/meguviz/meguviz-drawable-android/0.1.0-dev.1/meguviz-drawable-android-0.1.0-dev.1.aar --output docs/abi/0.1.0-dev.1/android-api.txt --metadata docs/abi/0.1.0-dev.1/android-api.json
python tools/test_dump_abi.py
```

The metadata records artifact and embedded-JAR SHA-256, API text SHA-256,
class count and `javap` version without machine paths. API rendering can vary
across JDK versions; use the recorded JDK for comparison. The tool snapshots
the supplied bytes before inspection, so the receipt identifies exactly what
was scanned. Test inputs exercise public/protected nested classes, hidden
enclosing classes, member visibility and stable JAR/AAR output.

This first candidate is a baseline, not proof of compatibility with an older
release. A future compatibility claim also needs old consumer binaries built
against the earlier artifact and run without recompilation on the new one.
This Java-level dump does not validate Kotlin metadata, runtime behavior,
Android API availability, shrinking or dynamic providers.

`0.1.0-dev.2/`, `0.1.0-dev.3/`, and `0.1.0-dev.4/` are separate later candidate snapshots.
Preserve the earlier directories instead of replacing them. The frozen `fixtures/abi/LegacyCoreClient`
exercises a small old core API subset without recompiling; it is not an
exhaustive compatibility guarantee.
