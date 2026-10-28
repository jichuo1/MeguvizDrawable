# MeguvizDrawable

MeguvizDrawable is an Android drawable library under development. Its packages
use the `io.github.jichuo1.meguviz.drawable` family namespace. The core is a
standalone Java module; the Android module uses the core without requiring
MeguvizAndroid, MeguvizUI, MeguvizRef, or Lumen. The current API is provisional
and no artifact has been published.

With JDK 17, run `./gradlew check` (Windows: `gradlew.bat check`) for the host
profile. To include the Android module use
`./gradlew -Pmeguviz.profile=android check` with an Android SDK installed.
Android, device, BIL integration, ABI and
performance gates are tracked separately and have not passed yet. The latest
feature changes are pending the planned consolidated verification.

The current Android implementation accepts an immutable `DrawingRecipe` and a
fixed density. It handles basic shapes, solid or stateful colors, linear
gradients and centered strokes; its platform behavior has not yet been
verified. Nonzero optical insets are explicitly unsupported; bounds that cannot
represent the requested stroke or gradient are rejected during preparation.
More complex paths, dynamic metrics, adapters and published coordinates are
still pending.

See [engineering status](docs/engineering/STATUS.md) and
[baseline](docs/engineering/BASELINE.md) for current progress and limits;
[verification](docs/engineering/VERIFICATION.md) records the initial check.
