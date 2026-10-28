# Independent View page experiment (pilot, version 1)

This is an independent synthetic page, not a BIL settings page or an estimate
of BIL-wide performance. The workload intentionally stresses repeated identical
state updates, so its ratios must not be applied to ordinary pages.

- Compare the previous `RecipeDrawable` implementation from source commit
  `cb4e6ed` with the current candidate artifact. The old class is copied into a
  test-only package with its class name changed; its drawing and update code
  remains unchanged. Both use the same model, bounds, colors and density.
- One Activity displays 64 visible fixed-size Views in an 8-by-8 grid. Each
  animation frame calls `setState` eight times per View using the same already
  installed state (512 redundant calls per frame), then invalidates every View
  so that an actual hardware window produces frames. There is no scrolling,
  network access or external data. This workload is deliberately artificial.
- Before each run, compare software bitmap pixels for both implementations at
  the fixture's bounds, color, state and alpha; fail rather than time unequal
  content. During the run confirm `Canvas.isHardwareAccelerated()` in the View.
- A run is one separately launched instrumentation process and Activity, not
  an individual frame. Skip the first 60 frame callbacks, then collect 180
  frame metrics. Report per-run frame total-duration median and p95, and the
  average UI-thread time spent in the 512-update loop. Capture dropped frame
  metric callbacks; treat a run as invalid if any were dropped or the target
  window was not hardware accelerated.
- Use release/R8 target with a local Android debug signature, same APK and
  device settings for both paths. Alternate AB/BA ordering. Begin with four
  independent paired pilot blocks, freeze any further confirmation sample size
  and practical threshold before collecting confirmation runs. Do not convert
  this pilot into a claim about BIL, real-world scrolling, battery or GPU time.
- Record APK/source SHA-256, build, device API, actual refresh rate, run order,
  raw run output, results, failures and any environmental interference. Do not
  clear device logs, user app data or install over an existing package.

FrameMetrics is available on API 24+, but this fixture requires API 27+ and is
not a compatibility test. Measurements are exploratory, not a release gate.
