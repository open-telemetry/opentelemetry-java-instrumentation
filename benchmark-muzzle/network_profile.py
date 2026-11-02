#!/usr/bin/env python3
"""Summarize Flight Recorder socket events without treating waits as CPU work."""

import argparse
from collections import Counter
import json
from pathlib import Path
import re


def duration_seconds(value):
    match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if match is None:
        raise ValueError(f"Invalid event duration: {value}")
    hours, minutes, seconds = match.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(seconds or 0)


def frames(values):
    stack = values.get("stackTrace")
    if not stack:
        return []
    return [
        frame["method"]["type"]["name"].replace("/", ".") + "." + frame["method"]["name"]
        for frame in stack["frames"]
    ]


def summarize(events):
    reads = []
    stacks = Counter()
    for event in events:
        values = event["values"]
        stack = frames(values)
        if event["type"] == "jdk.ExecutionSample":
            stacks.update(stack)
        if event["type"] != "jdk.SocketRead":
            continue
        reads.append({
            "start": values["startTime"], "seconds": duration_seconds(values["duration"]),
            "host": values["host"], "address": values["address"], "port": values["port"],
            "bytes": values["bytesRead"], "timeout_ms": values["timeout"],
            "end_of_stream": values["endOfStream"],
            "thread": values["eventThread"]["javaName"], "stack": stack,
        })
    if not reads:
        raise ValueError("Flight Recorder contains no socket-read events")
    return {
        "socket_reads": len(reads),
        "reads_over_10s": sum(read["seconds"] >= 10 for read in reads),
        "slowest_socket_reads": sorted(reads, key=lambda read: read["seconds"], reverse=True)[:50],
        "sampled_frame_counts": stacks.most_common(100),
        "interpretation": (
            "Socket durations can overlap across threads. They are not additive wall-time phases. "
            "A zero-byte socket read may be a timeout, end-of-stream or another failed read; "
            "interpret its stack, configured timeout and surrounding resource operation together. "
            "Execution-sample frame counts are diagnostic, not exact CPU percentages."
        ),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("events", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = summarize(json.loads(args.events.read_text(encoding="utf-8"))["recording"]["events"])
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
