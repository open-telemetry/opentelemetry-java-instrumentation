#!/usr/bin/env python3
"""Print concise diagnostics for Copilot review CLI JSONL output."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--tail", type=int, default=15)
    return parser.parse_args()


def collapse(value: str, limit: int = 240) -> str:
    collapsed = " ".join(value.split())
    if len(collapsed) <= limit:
        return collapsed
    return collapsed[: limit - 3] + "..."


def summarize_field(value: object) -> str:
    if isinstance(value, str):
        return collapse(value)
    if isinstance(value, bool):
        return str(value).lower()
    if isinstance(value, (int, float)):
        return str(value)
    if value is None:
        return "null"
    if isinstance(value, list):
        return f"list[{len(value)}]"
    if isinstance(value, dict):
        return f"object({', '.join(sorted(value.keys())[:6])})"
    return type(value).__name__


def summarize_event(event: dict[str, object]) -> str:
    parts: list[str] = []
    data = event.get("data")

    for key in ("type", "id"):
        if key in event:
            parts.append(f"{key}={summarize_field(event[key])}")

    if isinstance(data, dict):
        for key in (
            "role",
            "name",
            "tool",
            "toolName",
            "command",
            "status",
            "exitCode",
            "exit_code",
            "content",
            "error",
            "message",
        ):
            if key in data:
                parts.append(f"{key}={summarize_field(data[key])}")
        if len(parts) <= 2:
            parts.append(f"dataKeys={','.join(sorted(data.keys())[:8])}")

    return "; ".join(parts)


def main() -> None:
    args = parse_args()
    path = Path(args.input)

    if not path.exists():
        print(f"Diagnostics input missing: {path}")
        return

    raw_lines = path.read_text(encoding="utf-8").splitlines()
    print(f"Diagnostics for {path}")
    print(f"Line count: {len(raw_lines)}")

    event_types: Counter[str] = Counter()
    assistant_messages: list[str] = []
    tail_events: list[tuple[int, str]] = []

    for line_number, raw_line in enumerate(raw_lines, start=1):
        line = raw_line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
        except json.JSONDecodeError as exc:
            print(f"Invalid JSON on line {line_number}: {exc}")
            continue

        event_type = str(event.get("type", "<missing>"))
        event_types[event_type] += 1

        data = event.get("data")
        if event_type == "assistant.message" and isinstance(data, dict):
            content = data.get("content")
            if isinstance(content, str) and content.strip():
                assistant_messages.append(collapse(content, limit=500))

        tail_events.append((line_number, summarize_event(event)))
        if len(tail_events) > args.tail:
            tail_events.pop(0)

    print("Event types:")
    for event_type, count in event_types.most_common():
        print(f"  {event_type}: {count}")

    if assistant_messages:
        print("Last assistant message:")
        print(f"  {assistant_messages[-1]}")
    else:
        print("Last assistant message:")
        print("  <none>")

    print(f"Last {len(tail_events)} events:")
    for line_number, summary in tail_events:
        print(f"  line {line_number}: {summary}")


if __name__ == "__main__":
    main()