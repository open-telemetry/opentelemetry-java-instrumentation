#!/usr/bin/env python3
"""Prepare the deterministic review bundle for a PR.

CI entry point for the pr-review agentic workflow. Writes:

  <output-dir>/pr.diff
  <output-dir>/metadata.json
  <output-dir>/diff-scope.json
  <output-dir>/files/<repo-relative-path>     (post-change PR file contents)
  <output-dir>/knowledge/<name>.md            (review knowledge articles)
  <output-dir>/prompt.md                      (rendered persona prompt)

The findings path written into prompt.md is fixed at
``/tmp/gh-aw/agent/findings.json`` so gh-aw's auto-uploader includes it in the
``agent`` artifact. Override with --findings-path for local use.
"""

from __future__ import annotations

import argparse
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any
from urllib.parse import quote

from common import Summary, detect_repo, gh, gh_json, progress, write_json


HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
MAX_FILE_CHARS = 80_000
DEFAULT_FINDINGS_PATH = Path("/tmp/gh-aw/agent/findings.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--output-dir", type=Path, required=True, help="directory to write bundle into")
    parser.add_argument(
        "--findings-path",
        type=Path,
        default=DEFAULT_FINDINGS_PATH,
        help=f"path the agent must write findings JSON to (default: {DEFAULT_FINDINGS_PATH})",
    )
    return parser.parse_args()


def pr_metadata(pr: int, summary: Summary) -> dict[str, Any]:
    fields = ",".join(
        [
            "number",
            "title",
            "url",
            "baseRefName",
            "baseRefOid",
            "headRefName",
            "headRefOid",
            "headRepository",
            "headRepositoryOwner",
        ]
    )
    return gh_json(["pr", "view", str(pr), "--json", fields], summary)


def parse_diff(diff: str) -> dict[str, dict[str, Any]]:
    files: dict[str, dict[str, Any]] = {}
    current_file: str | None = None
    right_line = 0
    new_file_marker = False
    deleted_file_marker = False

    for line in diff.splitlines():
        if line.startswith("diff --git "):
            new_file_marker = False
            deleted_file_marker = False
            continue
        if line.startswith("new file mode"):
            new_file_marker = True
            continue
        if line.startswith("deleted file mode"):
            deleted_file_marker = True
            continue
        if line.startswith("+++ "):
            path = line[4:]
            if path == "/dev/null":
                current_file = None
                continue
            if path.startswith("b/"):
                path = path[2:]
            current_file = path
            status = "added" if new_file_marker else ("deleted" if deleted_file_marker else "modified")
            files.setdefault(current_file, {"changed_lines": set(), "hunks": [], "status": status})
            # right_line is (re)initialized by the next @@ hunk header.
            right_line = 0
            continue

        match = HUNK_RE.match(line)
        if match and current_file:
            hunk_start = int(match.group(1))
            line_count = int(match.group(2) or "1")
            hunk_end = hunk_start + line_count - 1 if line_count else hunk_start
            files[current_file]["hunks"].append([hunk_start, hunk_end])
            right_line = hunk_start
            continue

        if not current_file or not files[current_file]["hunks"]:
            continue
        if line.startswith("+") and not line.startswith("+++"):
            files[current_file]["changed_lines"].add(right_line)
            right_line += 1
        elif line.startswith(" "):
            right_line += 1
        elif line.startswith("-"):
            continue

    for info in files.values():
        info["changed_lines"] = sorted(info["changed_lines"])
    return files


def is_reviewable(path: str) -> bool:
    return not path.startswith("licenses/")


def read_file_excerpt(repo: str, ref: str, path: str, summary: Summary | None) -> str:
    endpoint = f"repos/{repo}/contents/{quote(path, safe='/')}?ref={quote(ref, safe='')}"
    text = gh(["api", "-H", "Accept: application/vnd.github.raw", endpoint], summary).stdout
    if len(text) > MAX_FILE_CHARS:
        return text[:MAX_FILE_CHARS] + "\n...[file truncated]...\n"
    return text


def pr_head_repo(metadata: dict[str, Any]) -> str:
    owner = ((metadata.get("headRepositoryOwner") or {}).get("login") or "").strip()
    repo = ((metadata.get("headRepository") or {}).get("name") or "").strip()
    if not owner or not repo:
        raise RuntimeError("PR metadata did not include head repository details")
    return f"{owner}/{repo}"


def list_knowledge_paths(base_repo: str, base_ref: str, summary: Summary) -> list[str]:
    endpoint = f"repos/{base_repo}/contents/.github/agents/knowledge?ref={quote(base_ref, safe='')}"
    entries = gh_json(["api", endpoint], summary)
    if not isinstance(entries, list):
        raise RuntimeError("GitHub contents API did not return the knowledge directory listing")
    paths: list[str] = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        path = entry.get("path")
        if entry.get("type") == "file" and isinstance(path, str) and path.endswith(".md"):
            paths.append(path)
    return sorted(paths)


def write_knowledge_bundle(base_repo: str, base_ref: str, directory: Path, summary: Summary) -> None:
    progress("Loading review knowledge files")
    directory.mkdir(parents=True, exist_ok=True)
    paths = [*list_knowledge_paths(base_repo, base_ref, summary), "docs/contributing/style-guide.md"]
    with ThreadPoolExecutor(max_workers=8) as pool:
        contents_by_path = dict(
            zip(paths, pool.map(lambda p: read_file_excerpt(base_repo, base_ref, p, None), paths))
        )
    for path in paths:
        destination = directory / Path(path).name
        destination.write_text(contents_by_path[path], encoding="utf-8")


def write_review_bundle(
    metadata: dict[str, Any],
    diff_text: str,
    diff_scope: dict[str, dict[str, Any]],
    directory: Path,
    summary: Summary,
    base_repo: str,
) -> None:
    progress(f"Preparing review bundle in {directory}")
    head_repo = pr_head_repo(metadata)
    head_ref = metadata["headRefOid"]
    base_ref = metadata["baseRefOid"]
    paths_to_fetch = [
        path
        for path, info in diff_scope.items()
        if is_reviewable(path) and info.get("changed_lines")
    ]
    with ThreadPoolExecutor(max_workers=8) as pool:
        file_contents = dict(
            zip(
                paths_to_fetch,
                pool.map(lambda p: read_file_excerpt(head_repo, head_ref, p, None), paths_to_fetch),
            )
        )

    files_dir = directory / "files"
    files_dir.mkdir(parents=True, exist_ok=True)
    write_knowledge_bundle(base_repo, base_ref, directory / "knowledge", summary)

    for path, contents in file_contents.items():
        destination = files_dir / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(contents, encoding="utf-8")

    (directory / "pr.diff").write_text(diff_text, encoding="utf-8")
    write_json(directory / "metadata.json", metadata)
    write_json(directory / "diff-scope.json", diff_scope)
    summary.temp_dir = str(directory)


def render_prompt(
    output_dir: Path,
    findings_path: Path,
    base_branch: str,
    diff_scope: dict[str, dict[str, Any]],
) -> str:
    bundled = sorted(p for p, info in diff_scope.items() if is_reviewable(p) and info.get("changed_lines"))
    deleted = sorted(p for p, info in diff_scope.items() if is_reviewable(p) and info.get("status") == "deleted")
    bundled_list = "\n".join(f"- {p}" for p in bundled) or "- (none)"
    deleted_list = "\n".join(f"- {p}" for p in deleted) or "- (none)"
    return f"""# PR review run

The persona file (imported by the workflow) defines the role, the bundle
layout, the JSON output contract, and the hard rules. The values below are
the per-PR data the persona cannot know.

- Bundle root: {output_dir}
- Diff: {output_dir}/pr.diff
- PR-changed file contents: {output_dir}/files/<repo-relative-path>
- Review knowledge: {output_dir}/knowledge/ (start with README.md)
- Base branch (the working tree is detached at this PR's base commit): {base_branch}
- Findings JSON output path: {findings_path}

Bundled PR files (post-change contents available under {output_dir}/files):
{bundled_list}

Deleted by this PR (do not read):
{deleted_list}
"""


def build_bundle(pr: int, output_dir: Path, findings_path: Path, summary: Summary) -> Path:
    metadata = pr_metadata(pr, summary)
    summary.pr_url = metadata.get("url")
    base_branch = metadata.get("baseRefName")
    if not isinstance(base_branch, str):
        raise RuntimeError("PR metadata did not include a base branch")
    base_repo = detect_repo(summary)
    progress("Collecting PR diff")
    diff_names = gh(["pr", "diff", str(pr), "--name-only"], summary).stdout.splitlines()
    diff_text = gh(["pr", "diff", str(pr), "--color", "never"], summary).stdout
    progress("Parsing PR diff hunks")
    diff_scope = parse_diff(diff_text)
    for path in diff_names:
        diff_scope.setdefault(path, {"changed_lines": [], "hunks": [], "status": "deleted"})

    output_dir.mkdir(parents=True, exist_ok=True)
    write_review_bundle(metadata, diff_text, diff_scope, output_dir, summary, base_repo)
    prompt = render_prompt(output_dir, findings_path, base_branch, diff_scope)
    prompt_path = output_dir / "prompt.md"
    prompt_path.write_text(prompt, encoding="utf-8")
    write_json(output_dir / "base.json", {"base_repo": base_repo, "base_branch": base_branch})
    progress(f"Wrote prompt to {prompt_path}")
    return prompt_path


def main() -> int:
    args = parse_args()
    summary = Summary(pr=args.pr)
    try:
        build_bundle(args.pr, args.output_dir, args.findings_path, summary)
    except Exception as e:  # pragma: no cover - surfaced to the workflow log
        print(f"ERROR: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
