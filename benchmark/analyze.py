"""Summarize independent-process medians; no external Python packages."""
import csv
import json
import random
import statistics
import sys


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: python benchmark/analyze.py <summary.csv>")
    with open(sys.argv[1], encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source))
    if len(rows) < 2:
        raise SystemExit("at least two independent processes are required")
    values = [float(row["median_paired_delta_percent"]) for row in rows]
    rng = random.Random(20261028)
    resampled = sorted(statistics.median(rng.choices(values, k=len(values))) for _ in range(10000))
    result = {
        "independent_jvm_processes": len(values),
        "metric": "median of within-fork median paired reductions, percent",
        "point_estimate": statistics.median(values),
        "observed_range": [min(values), max(values)],
        "bootstrap_95_percentile_interval": [resampled[249], resampled[9749]],
        "bootstrap_resamples": len(resampled),
        "bootstrap_seed": 20261028,
        "positive_processes": sum(value > 0 for value in values),
        "caveat": "Process bootstrap assumes independent, identically distributed runs; shared machine load and time correlation can violate that assumption.",
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
