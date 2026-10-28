# Independent Android View page pilot

This fixture tests an intentionally high volume of repeated identical state
updates on a real hardware-accelerated View page. It is not BIL and cannot
establish a BIL-wide or device-wide improvement. Read [PROTOCOL.md](PROTOCOL.md)
before collecting samples.

The baseline source is the production `RecipeDrawable` from commit `cb4e6ed`,
copied to a test-only package with the class renamed; `run-pilot.ps1` refuses to
run if it differs beyond those two changes. The candidate is the staged
`0.1.0-dev.3` artifact. The release app uses R8 and the standard local debug
signing configuration solely for device experiments.

On JDK 17 with the Android SDK installed, stage the final candidate, then:

```powershell
./gradlew.bat --offline --no-daemon -p benchmark/page :app:verifyCandidateResolution :app:assembleRelease :app:assembleReleaseAndroidTest
./benchmark/page/run-pilot.ps1 -Serial <device> -Adb <path-to-adb>
python benchmark/page/analyze.py <pilot-evidence-directory>
```

Check existing package names before installation. The runner refuses to replace
an existing package; `-UseMatchingInstalled` permits reuse only when both
installed APKs match the newly built bytes. It never uninstalls or clears data.
All run logs and device-specific details are written to the locally ignored
engineering evidence directory. This is an exploratory pilot; publication
requires a separately frozen confirmation protocol and independent samples.

For the separately registered 30-pair confirmation, use `-Phase confirmation`
and summarize with `python benchmark/page/analyze.py <directory> --phase confirmation`.
See [CONFIRMATION.md](CONFIRMATION.md) for stopping conditions, thresholds and
statistical limitations.
