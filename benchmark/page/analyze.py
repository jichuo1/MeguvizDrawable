#!/usr/bin/env python3
"""Summarize page benchmark runs by process; retain raw log files separately."""
import argparse
import json
from pathlib import Path
import re
import statistics
import random


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--phase", choices=("pilot", "confirmation"), default="pilot")
    args = parser.parse_args()
    rows = []
    for path in sorted(args.directory.glob("run-*.log")):
        data = path.read_text(encoding="utf-8", errors="replace")
        header = re.search(r"PAGEBENCH PASS path=(baseline|candidate) api=(\d+) refresh=([0-9.]+) pixel_hash=(-?\d+) callbacks=(\d+) dropped=(\d+)", data)
        frames = re.search(r"frames_ns=\[([^]]+)\]", data)
        updates = re.search(r"updates_ns=\[([^]]+)\]", data)
        pid = re.search(r"\bpid=(\d+)", data)
        if not header or not frames or not updates or not re.search(r"INSTRUMENTATION_CODE:\s*-1", data):
            raise ValueError(f"missing or failed run: {path.name}")
        frame_values = sorted(int(s.strip()) for s in frames.group(1).split(","))
        update_values = [int(s.strip()) for s in updates.group(1).split(",")]
        if len(frame_values) != 180 or len(update_values) != 180 or int(header.group(6)) != 0:
            raise ValueError(f"invalid sample count or dropped frames: {path.name}")
        rows.append({"file": path.name, "path": header.group(1), "api": int(header.group(2)),
                     "refresh_hz": float(header.group(3)), "pixel_hash": header.group(4),
                     "pid": int(pid.group(1)) if pid else None,
                     "frame_median_ms": statistics.median(frame_values) / 1e6,
                     "frame_p95_ms": frame_values[170] / 1e6,
                     "update_mean_ms": statistics.mean(update_values) / 1e6})
    expected_runs = 60 if args.phase == "confirmation" else 8
    if len(rows) != expected_runs or len({(r["api"], r["refresh_hz"], r["pixel_hash"]) for r in rows}) != 1:
        raise ValueError(f"expected {expected_runs} runs with identical API, refresh rate and pixel preflight")
    if args.phase == "confirmation" and (rows[0]["api"] != 37 or not 119.5 <= rows[0]["refresh_hz"] <= 120.5):
        raise ValueError("confirmation requires API 37 and 120 Hz")
    if args.phase == "confirmation" and (any(r["pid"] is None for r in rows) or len({r["pid"] for r in rows}) != len(rows)):
        raise ValueError("confirmation requires a distinct recorded PID for every run")
    pairs = []
    for i in range(0, len(rows), 2):
        pair = rows[i:i + 2]
        if {r["path"] for r in pair} != {"baseline", "candidate"}:
            raise ValueError("each block needs one baseline and one candidate")
        base = next(r for r in pair if r["path"] == "baseline")
        candidate = next(r for r in pair if r["path"] == "candidate")
        pairs.append({"block": i // 2 + 1, "order": [r["path"] for r in pair],
                      "frame_p95_delta_ms": candidate["frame_p95_ms"] - base["frame_p95_ms"],
                      "update_mean_delta_ms": candidate["update_mean_ms"] - base["update_mean_ms"]})
    result = {"phase": args.phase, "independent_runs": len(rows), "runs": rows, "pairs": pairs,
              "claim": "synthetic workload only; no BIL or general speedup claim"}
    if args.phase == "confirmation":
        deltas = [p["frame_p95_delta_ms"] for p in pairs]
        rng = random.Random(20261028)
        bootstrap = sorted(statistics.median(rng.choices(deltas, k=len(deltas))) for _ in range(10000))
        lower, upper = bootstrap[249], bootstrap[9749]
        result["primary"] = {"metric": "paired candidate-minus-baseline frame p95 ms",
                             "median_ms": statistics.median(deltas), "bootstrap_95_ms": [lower, upper],
                             "observed_gain_at_least_0_2ms": upper < -0.2,
                             "noninferior_at_0_5ms": upper < 0.5,
                             "regression_exceeds_0_5ms": lower > 0.5,
                             "assumption": "paired blocks exchangeable; same-device drift may violate this"}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
