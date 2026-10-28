# Frozen host confirmation protocol

The primary metric is the median of the seven within-round relative time
reductions `1 - LUT/direct` for each independent JVM. The fixture and measured
operations are `CorePilot.java`: eight fixed IDs, all 256 states in a
scrambled repeated order, 13 ordered rules, one million operations per round,
four warmup rounds and seven measured rounds per JVM. Candidate and baseline
order alternates between rounds and forks. The experimental LUT includes state
encoding, but excludes table construction and occupies 1024 payload bytes.

Run ten new sequential JVM processes after the pilot, with no change to the
fixture or production state matcher. Summarize each process using
`summarize-pilot.ps1`. For this one fixture only, an exploratory candidate is
interesting if the median of the ten per-fork paired improvements is at least
20%, and at least nine of ten forks show positive improvement. This is not an
acceptance gate for production or an Android/device speedup claim; it does not
include cold preparation, realistic mixed workloads, memory overhead beyond
table payload, thermal control, GC analysis, or the full semantic contract.
The candidate stays out of production pending those checks.

The pilot is not reused as confirmation. Each run records source SHA-256,
commit, JDK, architecture and raw rounds. Investigate failures and preserve
all data rather than selecting best rounds. Since the runs share one machine,
independent processes do not eliminate temporal or system-wide correlations.
After collecting the confirmation data, a secondary exploratory uncertainty
summary was added: a 95% percentile bootstrap interval for
the median of the process-level paired medians, resampling whole processes
10,000 times with seed 20261028. Its interpretation assumes independent and
identically distributed processes, which temporal drift can violate.
