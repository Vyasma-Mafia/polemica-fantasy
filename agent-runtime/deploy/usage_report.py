#!/usr/bin/env python3
"""Print numeric CLI usage only; never print log content or infer subscription cost."""
import argparse
import datetime as dt
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log-dir", type=Path, default=Path("/var/lib/polemica-ai-agent-runner/logs"))
    parser.add_argument("--hours", type=int, default=24)
    args = parser.parse_args()
    if not 1 <= args.hours <= 8760:
        parser.error("hours must be 1..8760")
    cutoff = dt.datetime.now(dt.timezone.utc).timestamp() - args.hours * 3600
    fields = ("input_tokens", "cached_input_tokens", "cache_write_input_tokens", "output_tokens", "reasoning_output_tokens")
    totals = {k: 0 for k in fields}
    available = {k: 0 for k in fields}
    runs = []
    for path in sorted(args.log_dir.glob("*.jsonl")):
        if path.name == "wake-checks.jsonl" or path.stat().st_mtime < cutoff:
            continue
        usage = {}
        completed = 0
        model = None
        for line in path.open():
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if not isinstance(event, dict):
                continue
            if event.get("type") == "run_manifest":
                model = event.get("model")
            if event.get("type") != "turn.completed":
                continue
            completed += 1
            counters = event.get("usage")
            if not isinstance(counters, dict):
                continue
            for key in fields:
                value = counters.get(key)
                if type(value) is int and value >= 0:
                    usage[key] = usage.get(key, 0) + value
                    totals[key] += value
                    available[key] += 1
        runs.append({"runId": path.stem, "model": model, "completedTurns": completed, "usage": usage or None})
    print(json.dumps({"windowHours": args.hours, "selection": "log modified in window",
        "totals": {k: v if available[k] else None for k, v in totals.items()},
        "turnsWithCounter": available, "runs": runs,
        "note": "Missing/redacted counters are unknown, not zero. Input includes cached input; reasoning is part of output. Not a subscription cost estimate."}, indent=2))


if __name__ == "__main__":
    main()
