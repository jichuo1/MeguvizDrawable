# Exploratory core benchmark

This dependency-free pilot compares ordered direct state matching against an
experimental eight-state lookup table and measures corner normalization. It is
not a production optimization, release test, Android benchmark, or a claim
about frame time. The LUT is built from a single fixture before timing; its
256 `int` entries consume 1024 bytes of payload, excluding arrays and objects.
Each lookup includes state-to-bit encoding. The fixture has 13 ordered rules,
all 256 states, and a zero terminator; preflight compares every table answer
against the direct matcher. It does not establish correctness for all rules,
arbitrary domains, or concurrent lifecycle behavior.

Run in PowerShell with JDK 17:

```powershell
./benchmark/run-pilot.ps1
```

The runner compiles the current Java sources into a new local build directory,
runs five separate JVM processes, and writes their raw output under the local
ignored engineering evidence directory. Each fork executes four warmup and
seven measurement rounds; AB/BA order alternates. One million operations form a
round, but rounds inside one process are not independent runs. Consume and
compare per-fork summaries, not millions of calls or individual rounds as
independent samples. The pilot is exploratory: establish a frozen protocol and
use separate confirmation runs only after relevant correctness tests pass.
For a performance improvement claim, include preparation, memory and
invalidation costs, realistic domains, and Android/device measurements.

To perform the frozen host-only confirmation described in [PROTOCOL.md](PROTOCOL.md),
run `./benchmark/run-pilot.ps1 -Phase confirmation -Forks 10` in a separate
invocation. Summarize either run with
`./benchmark/summarize-pilot.ps1 -Path <results-directory>`. Its paired delta
is computed per round before taking each fork's median. Save its CSV and run
`python benchmark/analyze.py <summary.csv>` for a seeded process-level
bootstrap interval; this interval relies on assumptions that shared machine
load and temporal drift may violate.
