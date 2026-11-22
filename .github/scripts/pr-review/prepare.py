"""Fetch a PR diff and copies of changed files without checking out the PR."""

from __future__ import annotations

import re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any
from urllib.parse import quote

from common import Summary, gh, gh_json, progress, write_json


HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
MAX_FILE_CHARS = 80_000


def pr_metadata(pr: int, summary: Summary) -> dict[str, Any]:
    fields = "number,state,url,baseRefOid,headRefOid,headRepository,headRepositoryOwner"
    return gh_json(["pr", "view", str(pr), "--json", fields], summary)


def parse_diff(diff: str) -> dict[str, dict[str, Any]]:
    files: dict[str, dict[str, Any]] = {}
    current: dict[str, Any] | None = None
    right_line = 0
    for line in diff.splitlines():
        if line.startswith("diff --git "):
            current = None
        elif current is None and line.startswith("+++ b/"):
            current = files.setdefault(line[6:], {"changed_lines": [], "hunks": []})
        elif current is not None:
            match = HUNK_RE.match(line)
            if match:
                right_line = int(match[1])
                current["hunks"].append([right_line, right_line + int(match[2] or "1") - 1])
            elif current["hunks"]:
                if line.startswith("+"):
                    current["changed_lines"].append(right_line)
                    right_line += 1
                elif line.startswith(" "):
                    right_line += 1
    return files


def read_file_excerpt(repo: str, ref: str, path: str) -> str:
    endpoint = f"repos/{repo}/contents/{quote(path, safe='/')}?ref={quote(ref, safe='')}"
    text = gh(["api", "-H", "Accept: application/vnd.github.raw", endpoint]).stdout
    if len(text) > MAX_FILE_CHARS:
        return text[:MAX_FILE_CHARS] + "\n...[file truncated]...\n"
    return text


def write_review_bundle(
    metadata: dict[str, Any],
    diff_text: str,
    diff_scope: dict[str, dict[str, Any]],
    directory: Path,
) -> None:
    progress(f"Preparing review bundle in {directory}")
    owner = ((metadata.get("headRepositoryOwner") or {}).get("login") or "").strip()
    repo = ((metadata.get("headRepository") or {}).get("name") or "").strip()
    if not owner or not repo:
        raise RuntimeError("PR metadata did not include head repository details")
    head = metadata["headRefOid"]
    paths = [path for path, scope in diff_scope.items() if scope["changed_lines"] and not path.startswith("licenses/")]
    for path in paths:
        if (
            PurePosixPath(path).is_absolute()
            or ".." in PurePosixPath(path).parts
            or PureWindowsPath(path).drive
            or "\\" in path
        ):
            raise RuntimeError(f"Invalid diff path: {path!r}")
    with ThreadPoolExecutor(max_workers=8) as pool:
        contents = list(pool.map(lambda path: read_file_excerpt(f"{owner}/{repo}", head, path), paths))
    for path, text in zip(paths, contents, strict=True):
        destination = directory / "files" / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(text, encoding="utf-8")
    (directory / "pr.diff").write_text(diff_text, encoding="utf-8")
    write_json(directory / "diff-scope.json", diff_scope)


def build_bundle(pr: int, directory: Path, summary: Summary) -> dict[str, Any]:
    metadata = pr_metadata(pr, summary)
    if metadata["state"] != "OPEN":
        raise RuntimeError("The PR is not open")
    summary.pr_url = metadata["url"]
    diff = gh(["pr", "diff", str(pr), "--color", "never"], summary).stdout
    write_review_bundle(metadata, diff, parse_diff(diff), directory)
    return metadata
