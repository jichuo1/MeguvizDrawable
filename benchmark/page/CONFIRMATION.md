# Frozen confirmation protocol: independent View page, v1

Freeze this protocol before collecting the confirmation samples. The pilot is
excluded from confirmation. The scene and frame collection are specified in
[PROTOCOL.md](PROTOCOL.md): 64 Views, 512 intentionally redundant identical
state updates per frame, a real hardware window, 60 warmup callbacks, then
180 measured frame metrics per run. This is a synthetic stress page, not BIL.

- Collect **30 independent paired blocks** on one API 37 device with the same
  release/R8 target APK, 120 Hz display mode, resolution, and USB power as the
  pilot. The instrumentation APK is rebuilt only to record its process ID;
  the measured target application and its workload must remain identical.
  Start separate instrumentation/Activity processes for every run and require
  distinct recorded process IDs. Alternate
  baseline→candidate and candidate→baseline. Do not treat frames within a run
  as independent samples.
- Primary endpoint: for each run, the p95 of `FrameMetrics.TOTAL_DURATION`;
  compute candidate minus baseline within each block. Summarize the median of
  the 30 differences and a 95% percentile bootstrap interval resampling whole
  blocks 10,000 times with seed `20261028`. This exploratory interval assumes
  blocks are exchangeable; shared device load and temporal drift can violate it.
- Predefined minimum meaningful gain: 0.2 ms/frame. A gain is demonstrated for
  this fixture only if the upper confidence limit is below -0.2 ms. A candidate
  is non-inferior under the 0.5 ms margin only if the upper limit is below
  +0.5 ms. If the lower confidence limit is above +0.5 ms, report a regression
  exceeding that margin. Otherwise, if the interval cannot exclude a regression
  larger than the margin, report INCONCLUSIVE. No frame rate or BIL-wide
  percentage is implied. Do not change these thresholds after collection.
- Secondary endpoint: each run's mean UI-thread time for its 512-update loop.
  It explains mechanism but is not the primary page-level success criterion.
- Before and after every pair, capture battery data and stop the campaign if
  USB power is disconnected, battery level is below 20%, battery temperature is
  unavailable or outside 15–40°C, the device disconnects, focus is lost,
  hardware Canvas is unavailable, a frame-metric callback is dropped, or the
  pixel preflight fails. Stop if app/test/APK or source hashes change. Keep all
  partial raw results and report a stopped campaign; never selectively discard
  slow samples or retry within the same confirmation dataset.
- Record actual display refresh for every run, source and staged artifact hashes,
  app/test APK hashes, device API and temperatures. Require every run to report
  119.5–120.5 Hz and the same preflight pixel checksum. Record battery before,
  between and after pairs; do not modify device refresh, compilation profile,
  user applications or background services to force a result.

The pilot showed p95 values near 7 ms in most runs and near 15 ms in some runs;
the spread is substantially larger than the proposed meaningful gain. A wide
confirmation interval must remain INCONCLUSIVE even if the point estimate is
favorable. GPU time, energy, scrolling and real BIL pages are not measured.
