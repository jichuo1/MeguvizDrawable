# MeguvizDrawable

MeguvizDrawable is an Android drawable library under development. The current
repository contains an initial dependency-free Java core for ordered state
matching and corner-radius normalization; it does not yet provide an Android
Drawable or a published artifact.

With JDK 17, run `./gradlew check` (Windows: `gradlew.bat check`). The `check`
task runs the executable core contract tests. Android, device, BIL integration,
ABI and performance gates are tracked separately and have not passed yet.

See [engineering status](docs/engineering/STATUS.md) and
[baseline](docs/engineering/BASELINE.md) for current progress and limits;
[verification](docs/engineering/VERIFICATION.md) records the initial check.
