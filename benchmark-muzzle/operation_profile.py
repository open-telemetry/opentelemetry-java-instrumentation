#!/usr/bin/env python3
"""Summarize overlapping Gradle operations without adding concurrent durations."""

import argparse
from collections import Counter
import json
from pathlib import Path


PRIORITY = ("transfer", "resolution", "assertion", "task", "configuration")


def summarize(path, command_ms=None):
    records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    if not records:
        raise ValueError("Empty operation profile")
    events = []
    counts = Counter()
    for record in records:
        category = record["category"]
        if category not in PRIORITY or record["end"] < record["start"]:
            raise ValueError("Invalid operation interval")
        counts[category] += 1
        events.extend(((record["start"], category, 1), (record["end"], category, -1)))
    active = Counter()
    exclusive = Counter()
    previous = min(event[0] for event in events)
    for timestamp, category, delta in sorted(events):
        chosen = next((name for name in PRIORITY if active[name]), "unattributed")
        exclusive[chosen] += timestamp - previous
        active[category] += delta
        previous = timestamp
    observed = max(event[0] for event in events) - min(event[0] for event in events)
    if command_ms is not None:
        if observed > command_ms + 1000:
            raise ValueError("Operation profile exceeds the measured command duration")
        exclusive["unattributed"] += max(0, command_ms - observed)
    transfers = [record for record in records if record["category"] == "transfer"]
    resources = Counter()
    for record in transfers:
        name = record.get("location", record["name"])
        kind = "jar" if name.endswith(".jar") else "metadata"
        resources[kind] += 1
    return {
        "operation_counts": dict(counts),
        "exclusive_wall_ms": dict(exclusive),
        "observed_interval_ms": observed,
        "transfer_bytes": sum(record.get("bytes", 0) for record in transfers),
        "transfer_resource_counts": dict(resources),
        "failed_transfer_operations": sum(bool(record.get("failure")) for record in transfers),
        "missing_transfer_operations": sum(bool(record.get("missing")) for record in transfers),
        "repeated_resources": [
            {"location": location, "operations": count}
            for location, count in Counter(
                record.get("location", record["name"]) for record in transfers
            ).most_common(30)
        ],
        "slowest_operations": sorted(records, key=lambda row: row["end"] - row["start"], reverse=True)[:30],
        "slowest_transfers": sorted(transfers, key=lambda row: row["end"] - row["start"], reverse=True)[:30],
        "interpretation": (
            "Exclusive wall-time classification uses transfer > resolution > assertion > task > configuration. "
            "It is not a critical-path attribution or CPU profile. Resolution without an observed transfer can "
            "include CPU, scheduling and lock waits. Transfer operations cover Gradle resource reads, not "
            "Aether's independent version-discovery HTTP client. Resource reads include HTTP-client setup, "
            "queuing and response waits, not just bytes on the wire. Summed operation durations overlap."
        ),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--command-ms", type=float)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(summarize(args.profile, args.command_ms), indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
