#!/usr/bin/env python3
"""Extract the final assistant message from review CLI JSONL output."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--final-message-output")
    return parser.parse_args()


def strip_json_fence(value: str) -> str:
    stripped = value.strip()
    lines = stripped.splitlines()
    if (
        len(lines) >= 3
        and lines[0].strip().lower() in {"```", "```json"}
        and lines[-1].strip().startswith("```")
    ):
        return "\n".join(lines[1:-1]).strip()
    return stripped


def extract_final_message(path: Path) -> str:
    final_message: str | None = None

    with path.open(encoding="utf-8") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if not line:
                continue
            event = json.loads(line)
            if event.get("type") != "assistant.message":
                continue
            content = event["data"]["content"]
            if isinstance(content, str):
                final_message = content

    if not final_message or not final_message.strip():
        raise ValueError("Review output did not contain a non-empty assistant.message")

    return final_message.strip()


def validate_report_json(report: str) -> dict[str, object]:
    parsed = json.loads(strip_json_fence(report))
    if not isinstance(parsed, dict):
        raise ValueError(f"Review report must be a JSON object, got {type(parsed).__name__}")
    return parsed


def main() -> None:
    args = parse_args()
    report = extract_final_message(Path(args.input))

    # Write the raw final message *before* JSON validation so that, on validation
    # failure, the diagnostic artifact is preserved for post-mortem. In that case
    # `--output` is intentionally not produced and the caller treats the run as failed.
    if args.final_message_output:
        Path(args.final_message_output).write_text(report + "\n", encoding="utf-8")

    parsed = validate_report_json(report)
    Path(args.output).write_text(json.dumps(parsed, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()