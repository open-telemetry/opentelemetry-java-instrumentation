#!/usr/bin/env python3
#
# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

"""Find repeated non-parameterized test bodies that are candidates for @ParameterizedTest.

This is a heuristic scanner. It groups non-parameterized JUnit test methods inside the same file
by a normalized method-body shape and reports repeated groups.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


TEST_PATH_MARKERS = ("/src/test/", "/testing/src/main/")
TEST_EXTENSIONS = {".java", ".scala"}
METHOD_RE = re.compile(
    r"^(\s*@(?:[^\n]+)\n)+\s*(?:public|protected|private)?\s*(?:static\s+)?"
    r"(?:final\s+)?[\w\[\]<>?, @]+\s+(\w+)\s*\([^;{)]*\)\s*(?:throws [^{]+)?\{",
    re.M,
)
COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT_RE = re.compile(r"//.*")
STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_RE = re.compile(r"'(?:\\.|[^'\\])'")
NUMBER_RE = re.compile(r"\b\d+(?:\.\d+)?(?:[dDfFlL])?\b")
IDENTIFIER_RE = re.compile(r"\b[A-Za-z_][A-Za-z0-9_]*\b")

KEYWORDS = {
    "if",
    "else",
    "for",
    "while",
    "switch",
    "case",
    "return",
    "new",
    "throw",
    "try",
    "catch",
    "finally",
    "true",
    "false",
    "null",
    "this",
    "super",
    "class",
    "instanceof",
    "do",
    "break",
    "continue",
}


@dataclass
class MethodRecord:
  path: str
  line: int
  name: str
  lines: int
  normalized_length: int
  normalized_hash: str


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument(
      "--root",
      default=".",
      help="Repository root to scan. Defaults to the current directory.",
  )
  parser.add_argument(
      "--format",
      choices=("text", "json"),
      default="text",
      help="Output format.",
  )
  parser.add_argument(
      "--top",
      type=int,
      default=25,
      help="Maximum number of groups to include in text output.",
  )
  parser.add_argument(
      "--min-normalized-length",
      type=int,
      default=80,
      help="Discard tiny test bodies after normalization.",
  )
  parser.add_argument(
      "--likely-max-lines",
      type=float,
      default=25.0,
      help="Maximum average test-body line count for a group to be labeled likely-actionable.",
  )
  parser.add_argument(
      "--likely-min-group-size",
      type=int,
      default=2,
      help="Minimum repeated methods in a group for likely-actionable classification.",
  )
  return parser.parse_args()


def iter_test_files(root: Path) -> list[Path]:
  return sorted(
      path
      for path in root.rglob("*")
      if path.suffix in TEST_EXTENSIONS
      and any(marker in path.as_posix() for marker in TEST_PATH_MARKERS)
  )


def find_matching_brace(text: str, open_idx: int) -> int:
  depth = 0
  in_string: str | None = None
  escaping = False
  i = open_idx
  while i < len(text):
    char = text[i]
    if in_string:
      if escaping:
        escaping = False
      elif char == "\\":
        escaping = True
      elif char == in_string:
        in_string = None
    else:
      if char in ('"', "'"):
        in_string = char
      elif char == "{":
        depth += 1
      elif char == "}":
        depth -= 1
        if depth == 0:
          return i
    i += 1
  return -1


def strip_comments(text: str) -> str:
  return LINE_COMMENT_RE.sub("", COMMENT_RE.sub("", text))


def normalize_body(body: str) -> str:
  body = strip_comments(body)
  body = STRING_RE.sub("STR", body)
  body = CHAR_RE.sub("CHAR", body)
  body = NUMBER_RE.sub("NUM", body)

  def replace_identifier(match: re.Match[str]) -> str:
    identifier = match.group(0)
    if identifier in KEYWORDS:
      return identifier
    index = match.end()
    while index < len(body) and body[index].isspace():
      index += 1
    if index < len(body) and body[index] == "(":
      return identifier
    if identifier.isupper() or "_" in identifier:
      return "CONST"
    return "id"

  body = IDENTIFIER_RE.sub(replace_identifier, body)
  return " ".join(body.split())


def extract_method_records(path: Path, min_normalized_length: int) -> list[MethodRecord]:
  text = path.read_text(errors="ignore")
  records = []
  for match in METHOD_RE.finditer(text):
    annotated_signature = match.group(0)
    if "@Test" not in annotated_signature or "@ParameterizedTest" in annotated_signature:
      continue
    open_idx = match.end() - 1
    close_idx = find_matching_brace(text, open_idx)
    if close_idx == -1:
      continue
    body = text[open_idx + 1 : close_idx]
    normalized = normalize_body(body)
    if len(normalized) < min_normalized_length:
      continue
    records.append(
        MethodRecord(
            path=path.as_posix(),
            line=text.count("\n", 0, match.start()) + 1,
            name=match.group(2),
            lines=body.count("\n") + 1,
            normalized_length=len(normalized),
            normalized_hash=hashlib.md5(normalized.encode("utf-8")).hexdigest(),
        )
    )
  return records


def group_candidates(
    records: list[MethodRecord], likely_max_lines: float, likely_min_group_size: int
) -> list[dict[str, object]]:
  by_file: dict[str, list[MethodRecord]] = defaultdict(list)
  for record in records:
    by_file[record.path].append(record)

  groups: list[dict[str, object]] = []
  for path, file_records in by_file.items():
    by_hash: dict[str, list[MethodRecord]] = defaultdict(list)
    for record in file_records:
      by_hash[record.normalized_hash].append(record)
    for repeated in by_hash.values():
      if len(repeated) < 2:
        continue
      repeated.sort(key=lambda record: record.line)
      avg_lines = sum(record.lines for record in repeated) / len(repeated)
      groups.append(
          {
              "path": path,
              "count": len(repeated),
              "avg_lines": round(avg_lines, 1),
              "normalized_length": repeated[0].normalized_length,
              "likely_actionable": len(repeated) >= likely_min_group_size
              and avg_lines <= likely_max_lines,
              "methods": [
                  {"name": record.name, "line": record.line, "lines": record.lines}
                  for record in repeated
              ],
          }
      )
  groups.sort(key=lambda group: (-group["count"], group["avg_lines"], group["path"]))
  return groups


def build_report(
    files_scanned: int, records: list[MethodRecord], groups: list[dict[str, object]]
) -> dict[str, object]:
  group_size_counts = Counter(group["count"] for group in groups)
  likely_groups = [group for group in groups if group["likely_actionable"]]
  return {
      "files_scanned": files_scanned,
      "test_methods_scanned": len(records),
      "candidate_groups": len(groups),
      "likely_actionable_groups": len(likely_groups),
      "group_size_counts": dict(sorted(group_size_counts.items(), reverse=True)),
      "candidates": groups,
  }


def print_text_report(report: dict[str, object], top: int) -> None:
  print(f"files_scanned: {report['files_scanned']}")
  print(f"test_methods_scanned: {report['test_methods_scanned']}")
  print(f"candidate_groups: {report['candidate_groups']}")
  print(f"likely_actionable_groups: {report['likely_actionable_groups']}")
  print("group_size_counts:", report["group_size_counts"])
  print()
  print("top_candidates:")
  for group in report["candidates"][:top]:
    methods = ", ".join(f"{method['name']}:{method['line']}" for method in group["methods"])
    status = "likely" if group["likely_actionable"] else "review"
    print(
        f"[{status}] count={group['count']} avg_lines={group['avg_lines']} "
        f"path={group['path']} methods={methods}"
    )


def main() -> int:
  args = parse_args()
  root = Path(args.root).resolve()
  files = iter_test_files(root)
  records: list[MethodRecord] = []
  for path in files:
    records.extend(extract_method_records(path, args.min_normalized_length))
  groups = group_candidates(records, args.likely_max_lines, args.likely_min_group_size)
  report = build_report(len(files), records, groups)

  if args.format == "json":
    json.dump(report, sys.stdout, indent=2)
    print()
  else:
    print_text_report(report, args.top)
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
