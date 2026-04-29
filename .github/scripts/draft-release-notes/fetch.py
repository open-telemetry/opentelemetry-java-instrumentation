#!/usr/bin/env python3
"""Generate raw changelog draft output and a local review bundle.

This script is the source of truth for release-note draft generation. It emits
the raw markdown scaffold on stdout and prepares a local bundle of per-PR
patches and metadata under build/changelog-bundle/.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable


REPO = "open-telemetry/opentelemetry-java-instrumentation"
REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_BUNDLE_DIR = REPO_ROOT / "build" / "changelog-bundle"
AUTHOR_FILTER = r"^(?!renovate\[bot\] )"
SRC_MAIN_JAVA_PATHSPEC = "*/src/main/**/*.java"
PR_SUFFIX_RE = re.compile(r"\s*\(#(\d+)\)$")
VERSION_RE = re.compile(r'val stableVersion = "(\d+\.\d+\.\d+)')
ISSUE_REF_RE = re.compile(r"(?:issues|pull)/(\d+)|(?<![A-Za-z0-9/])#(\d+)\b")
GH_FETCH_WORKERS = 8
GH_FETCH_RETRIES = 3
GH_FETCH_RETRY_DELAY = 5.0


@dataclass
class Candidate:
    commit_hash: str
    subject: str
    pr_number: int | None
    files: list[str]
    touches_src_main: bool
    deprecated_added: bool
    deprecated_removed: bool

    @property
    def bundle_name(self) -> str:
        if self.pr_number is not None:
            return str(self.pr_number)
        return f"commit-{self.commit_hash[:12]}"

    @property
    def bundle_group(self) -> str:
        return "prs" if self.pr_number is not None else "commits"

    @property
    def label(self) -> str:
        """Human-readable identifier used in logs and markdown headings."""
        if self.pr_number is not None:
            return f"#{self.pr_number}"
        return f"commit {self.commit_hash[:12]}"

    @property
    def review_priority(self) -> str:
        if self.deprecated_added or self.deprecated_removed:
            return "high"
        if self.touches_src_main:
            return "normal"
        return "low"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("range", nargs="?", default=None)
    parser.add_argument(
        "--skip-bundle",
        action="store_true",
        help="Skip generating the local changelog bundle.",
    )
    parser.add_argument(
        "--refetch",
        action="store_true",
        help="Re-download every PR even if its bundle already exists. "
        "By default, fetch is incremental: PRs whose commit hash matches "
        "the existing meta.json are reused.",
    )

    return parser.parse_args()


def run_command(args: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=check,
    )


def load_json(args: list[str]) -> Any:
    result = run_command(args)
    return json.loads(result.stdout)


def load_json_with_retry(args: list[str]) -> Any:
    """Run a `gh` command with retries to absorb transient API failures."""
    last_error: subprocess.CalledProcessError | None = None
    for attempt in range(1, GH_FETCH_RETRIES + 1):
        try:
            return load_json(args)
        except subprocess.CalledProcessError as e:
            last_error = e
            if attempt == GH_FETCH_RETRIES:
                break
            warn(
                f"gh command failed (attempt {attempt}/{GH_FETCH_RETRIES}): "
                f"{' '.join(args)}\nstderr: {e.stderr}"
            )
            time.sleep(GH_FETCH_RETRY_DELAY * attempt)
    assert last_error is not None
    raise last_error


def warn(message: str) -> None:
    print(message, file=sys.stderr)


def configure_standard_streams() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


def get_version() -> str:
    text = (REPO_ROOT / "version.gradle.kts").read_text(encoding="utf-8")
    match = VERSION_RE.search(text)
    if match is None:
        raise RuntimeError("Could not determine stableVersion from version.gradle.kts")
    return match.group(1)


def compute_default_range(version: str) -> str:
    match = re.fullmatch(r"(\d+)\.(\d+)\.0", version)
    if match is None:
        raise RuntimeError(f"unexpected version: {version}")

    major = int(match.group(1))
    minor = int(match.group(2))
    changelog = (REPO_ROOT / "CHANGELOG.md").read_text(encoding="utf-8")

    if minor == 0:
        prior_major = major - 1
        prior_minor_match = re.search(
            rf"^## Version {prior_major}\.(\d+)\..*$",
            changelog,
            re.MULTILINE,
        )
        if prior_minor_match is None:
            return "HEAD"
        prior_minor = int(prior_minor_match.group(1))
        return f"v{prior_major}.{prior_minor}.0..HEAD"

    return f"v{major}.{minor - 1}.0..HEAD"


def get_commit_hashes(range_spec: str) -> list[str]:
    result = run_command(
        [
            "git",
            "log",
            "--reverse",
            "--perl-regexp",
            f"--author={AUTHOR_FILTER}",
            "--pretty=format:%H",
            range_spec,
        ]
    )
    hashes = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    return hashes


def get_commit_subject(commit_hash: str) -> str:
    result = run_command(["git", "log", "--format=%s", "-n", "1", commit_hash])
    return result.stdout.strip()


def get_commit_files(commit_hash: str) -> list[str]:
    result = run_command(["git", "diff-tree", "--no-commit-id", "--name-only", "-r", commit_hash])
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def is_automated_review_commit(subject: str) -> bool:
    return subject.startswith("Review fixes for ")


# A file is "user-facing runtime" iff it sits under `/src/main/` and is not
# inside a testing, smoke-test, or docs module. Everything else (tests, docs,
# build scripts, CI config, gradle wrappers, top-level properties, etc.) has
# no `/src/main/` in its path by construction and is therefore non-runtime
# without needing an explicit allow-list.
_NON_USER_FACING_PREFIXES = (
    "smoke-tests/",
    "smoke-tests-otel-starter/",
    "instrumentation-docs/",
)
_NON_USER_FACING_SUBSTRINGS = ("/testing/", "-testing/")


def touches_user_facing_src_main(files: list[str]) -> bool:
    """Return True if any file is a user-facing /src/main/ runtime source."""
    return any(
        "/src/main/" in f
        and not f.startswith(_NON_USER_FACING_PREFIXES)
        and not any(s in f for s in _NON_USER_FACING_SUBSTRINGS)
        for f in files
    )


def extract_pr_number(subject: str) -> int | None:
    match = PR_SUFFIX_RE.search(subject)
    if match is None:
        return None
    return int(match.group(1))


def trim_pr_suffix(subject: str) -> str:
    return PR_SUFFIX_RE.sub("", subject).rstrip()


def format_subject_as_entry(subject: str) -> str:
    pr_number = extract_pr_number(subject)
    summary = trim_pr_suffix(subject)
    if pr_number is None:
        return summary
    return (
        f"{summary}\n"
        f"  ([#{pr_number}](https://github.com/{REPO}/pull/{pr_number}))"
    )


def count_deprecated_deltas(commit_hash: str) -> tuple[int, int]:
    result = run_command(
        ["git", "diff-tree", "-p", commit_hash, "--", SRC_MAIN_JAVA_PATHSPEC],
        check=False,
    )
    added_count = 0
    removed_count = 0
    for line in result.stdout.splitlines():
        if "@Deprecated" not in line:
            continue
        if line.startswith("+") and not line.startswith("++"):
            added_count += 1
        elif line.startswith("-") and not line.startswith("--"):
            removed_count += 1
    return added_count, removed_count


def build_candidates(range_spec: str) -> list[Candidate]:
    candidates: list[Candidate] = []
    hashes = get_commit_hashes(range_spec)
    warn(f"Inspecting {len(hashes)} commit(s) in {range_spec}...")
    for commit_hash in hashes:
        subject = get_commit_subject(commit_hash)
        if is_automated_review_commit(subject):
            continue

        files = get_commit_files(commit_hash)
        added_count, removed_count = count_deprecated_deltas(commit_hash)
        candidates.append(
            Candidate(
                commit_hash=commit_hash,
                subject=subject,
                pr_number=extract_pr_number(subject),
                files=files,
                touches_src_main=touches_user_facing_src_main(files),
                deprecated_added=added_count > removed_count,
                deprecated_removed=removed_count > added_count,
            )
        )
    return candidates


def get_since_date(range_spec: str) -> str:
    if range_spec == "HEAD":
        # Use the unfiltered oldest commit so we don't miss PRs merged before
        # the oldest non-renovate commit in a first-release scenario.
        result = run_command(["git", "rev-list", "--max-parents=0", "HEAD"])
        oldest_commit = result.stdout.strip().splitlines()[0]
    else:
        match = re.fullmatch(r"(.+)\.\.(.+)", range_spec)
        if match is None:
            raise RuntimeError(f"Invalid range format: {range_spec}")
        oldest_commit = run_command(["git", "rev-parse", match.group(1)]).stdout.strip()

    result = run_command(["git", "show", "-s", "--format=%ci", oldest_commit])
    return result.stdout.strip().split(" ", 1)[0]


def fetch_labeled_prs(range_spec: str, label: str) -> list[dict[str, Any]]:
    since_date = get_since_date(range_spec)
    data = load_json(
        [
            "gh",
            "pr",
            "list",
            "--repo",
            REPO,
            "--label",
            label,
            "--state",
            "merged",
            "--search",
            f"merged:>={since_date}",
            "--json",
            "number,title",
        ]
    )
    return [item for item in data if isinstance(item, dict)]


def render_labeled_prs(title: str, items: list[dict[str, Any]]) -> list[str]:
    if not items:
        return []
    lines = [title, ""]
    for item in items:
        pr_number = item.get("number")
        pr_title = str(item.get("title", "")).strip()
        if not isinstance(pr_number, int) or not pr_title:
            continue
        lines.append(f"- {pr_title}")
        lines.append(f"  ([#{pr_number}](https://github.com/{REPO}/pull/{pr_number}))")
    lines.append("")
    return lines


def get_patch_from_git(commit_hash: str) -> str:
    result = run_command(["git", "show", "--format=medium", "--unified=10", commit_hash], check=False)
    if result.returncode != 0:
        return ""
    return result.stdout or ""


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_json(path: Path, data: Any) -> None:
    write_text(path, json.dumps(data, indent=2, sort_keys=True) + "\n")


def get_author_name(author: Any) -> str:
    if isinstance(author, dict):
        for key in ("login", "name"):
            value = author.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    if isinstance(author, str) and author.strip():
        return author.strip()
    return "unknown"


def render_text_block(title: str, text: str, empty_message: str) -> str:
    body = text.strip()
    if not body:
        body = empty_message
    return f"# {title}\n\n{body}\n"


def render_discussion_markdown(title: str, items: Any, item_type: str) -> str:
    lines = [f"# {title}", ""]
    if not isinstance(items, list) or not items:
        lines.append(f"No {item_type} available.")
        lines.append("")
        return "\n".join(lines)

    for index, item in enumerate(items, start=1):
        if not isinstance(item, dict):
            continue
        lines.append(f"## {index}. {get_author_name(item.get('author'))}")
        url = item.get("url")
        created_at = item.get("createdAt")
        state = item.get("state")
        if isinstance(url, str) and url.strip():
            lines.append(f"URL: {url.strip()}")
        if isinstance(created_at, str) and created_at.strip():
            lines.append(f"Created: {created_at.strip()}")
        if isinstance(state, str) and state.strip():
            lines.append(f"State: {state.strip()}")
        lines.append("")
        body = str(item.get("body") or "").strip()
        if body:
            lines.append(body)
            lines.append("")
        else:
            lines.append("(no body)")
            lines.append("")
    return "\n".join(lines)


def extract_reference_numbers(texts: list[str], excluded_numbers: set[int]) -> list[int]:
    references: set[int] = set()
    for text in texts:
        for match in ISSUE_REF_RE.finditer(text):
            number = match.group(1) or match.group(2)
            if number is None:
                continue
            parsed = int(number)
            if parsed in excluded_numbers:
                continue
            references.add(parsed)
    return sorted(references)


def discussion_texts(pr_data: dict[str, Any]) -> list[str]:
    """Return body + all comment/review bodies as strings for ref extraction."""
    texts = [str(pr_data.get("body") or "")]
    for key in ("comments", "reviews"):
        for item in pr_data.get(key) or []:
            if isinstance(item, dict):
                texts.append(str(item.get("body") or ""))
    return texts


def fetch_parallel(
    fn: Callable[[int], dict[str, Any]], numbers: list[int]
) -> dict[int, dict[str, Any]]:
    with ThreadPoolExecutor(max_workers=GH_FETCH_WORKERS) as ex:
        return dict(zip(numbers, ex.map(fn, numbers)))


def fetch_pr_data(pr_number: int) -> dict[str, Any]:
    return load_json_with_retry(
        [
            "gh",
            "pr",
            "view",
            str(pr_number),
            "--repo",
            REPO,
            "--json",
            "number,title,mergeCommit,author,labels,files,body,comments,reviews,state,url",
        ]
    )


def fetch_ref_data(ref_number: int) -> dict[str, Any]:
    return load_json_with_retry(
        [
            "gh",
            "issue",
            "view",
            str(ref_number),
            "--repo",
            REPO,
            "--json",
            "number,title,author,body,comments,labels,state,url",
        ]
    )


def _load_existing_meta(path: Path) -> dict[str, Any] | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def _is_candidate_fresh(candidate_dir: Path, candidate: Candidate) -> bool:
    """Return True if the on-disk bundle for this candidate is usable as-is."""
    meta_path = candidate_dir / "meta.json"
    patch_path = candidate_dir / "patch.diff"
    if not meta_path.exists() or not patch_path.exists():
        return False
    existing = _load_existing_meta(meta_path)
    if not isinstance(existing, dict):
        return False
    return existing.get("commit_hash") == candidate.commit_hash


def prepare_bundle(
    bundle_dir: Path,
    version: str,
    range_spec: str,
    candidates: list[Candidate],
    breaking_prs: list[dict[str, Any]],
    deprecation_prs: list[dict[str, Any]],
    refetch: bool = False,
) -> None:
    (bundle_dir / "prs").mkdir(parents=True, exist_ok=True)
    (bundle_dir / "commits").mkdir(parents=True, exist_ok=True)
    (bundle_dir / "refs").mkdir(parents=True, exist_ok=True)

    # Split candidates into reusable (meta.json + patch.diff already match
    # current commit hash) and needing a fresh fetch. Reusable entries avoid
    # burning gh API calls on PRs that haven't changed since last run.
    reusable: set[int] = set()  # indices into `candidates`
    to_fetch: list[Candidate] = []
    for i, c in enumerate(candidates):
        candidate_dir = bundle_dir / c.bundle_group / c.bundle_name
        if not refetch and _is_candidate_fresh(candidate_dir, c):
            reusable.add(i)
        else:
            to_fetch.append(c)

    warn(
        f"Candidates: {len(candidates)} total; "
        f"reusing {len(reusable)}, (re)fetching {len(to_fetch)}"
    )

    # Pre-fetch PR metadata in parallel. gh spawns a subprocess per call, so
    # a release with 100+ PRs is dominated by wall time if run serially.
    pr_numbers_to_fetch = [c.pr_number for c in to_fetch if c.pr_number is not None]
    if pr_numbers_to_fetch:
        warn(f"Fetching metadata for {len(pr_numbers_to_fetch)} PR(s)...")
    pr_data_map = fetch_parallel(fetch_pr_data, pr_numbers_to_fetch)

    # Collect referenced issue/PR numbers from newly-fetched PRs' discussion
    # text. Reused PRs already have their linked-issues.json on disk.
    pr_refs: dict[int, list[int]] = {
        c.pr_number: extract_reference_numbers(
            discussion_texts(pr_data_map[c.pr_number]), {c.pr_number}
        )
        for c in to_fetch
        if c.pr_number is not None
    }
    # Fetch only refs we don't already have on disk (unless refetch).
    refs_needed = sorted({n for refs in pr_refs.values() for n in refs})
    refs_to_fetch = [
        n for n in refs_needed
        if refetch or not (bundle_dir / "refs" / str(n) / "meta.json").exists()
    ]
    if refs_to_fetch:
        warn(f"Fetching metadata for {len(refs_to_fetch)} referenced issue(s)/PR(s)...")
    ref_data_map = fetch_parallel(fetch_ref_data, refs_to_fetch)

    manifest_candidates: list[dict[str, Any]] = []
    index_lines = [
        "# Changelog Bundle",
        "",
        f"Version: `{version}`",
        f"Range: `{range_spec}`",
        "",
        "This bundle is intended to support diff-first changelog review.",
        "",
    ]

    # Track ref numbers that should remain on disk across this run.
    live_refs: set[int] = set()

    total = len(candidates)
    for index, candidate in enumerate(candidates, start=1):
        candidate_dir = bundle_dir / candidate.bundle_group / candidate.bundle_name

        if (index - 1) in reusable:
            warn(f"[{index}/{total}] {candidate.label} (reused)")
            entry = _reuse_candidate(candidate_dir, live_refs)
        else:
            warn(f"[{index}/{total}] {candidate.label}")
            entry = _fetch_candidate(
                candidate, candidate_dir, bundle_dir,
                pr_data_map, pr_refs, ref_data_map, live_refs,
            )

        manifest_candidates.append(entry)
        _append_index_entry(index_lines, candidate, entry)

    _prune_stale_dirs(bundle_dir, candidates, live_refs)

    manifest = {
        "bundle_dir": str(bundle_dir.relative_to(REPO_ROOT)).replace("\\", "/"),
        "candidates": manifest_candidates,
        "deprecation_prs": deprecation_prs,
        "generated_by": ".github/scripts/draft-release-notes/fetch.py",
        "range": range_spec,
        "repo": REPO,
        "version": version,
        "breaking_change_prs": breaking_prs,
    }
    write_json(bundle_dir / "manifest.json", manifest)
    write_text(bundle_dir / "index.md", "\n".join(index_lines) + "\n")


def _reuse_candidate(candidate_dir: Path, live_refs: set[int]) -> dict[str, Any]:
    """Reuse path: load the existing meta.json and propagate its refs.

    Note: labels/url are not refreshed for reused PRs; that's acceptable
    since the commit hasn't changed.
    """
    existing_meta = _load_existing_meta(candidate_dir / "meta.json") or {}
    # Propagate any refs this PR previously referenced so they are not pruned.
    for ref in existing_meta.get("issue_refs") or []:
        if isinstance(ref, int):
            live_refs.add(ref)
    return existing_meta


def _fetch_candidate(
    candidate: Candidate,
    candidate_dir: Path,
    bundle_dir: Path,
    pr_data_map: dict[int, dict[str, Any]],
    pr_refs: dict[int, list[int]],
    ref_data_map: dict[int, dict[str, Any]],
    live_refs: set[int],
) -> dict[str, Any]:
    """Fetch path: write patch, body, comments, reviews, linked refs, meta."""
    candidate_dir.mkdir(parents=True, exist_ok=True)

    write_text(candidate_dir / "patch.diff", get_patch_from_git(candidate.commit_hash))

    pr_data: dict[str, Any] = (
        pr_data_map[candidate.pr_number] if candidate.pr_number is not None else {}
    )
    labels = [
        str(lbl.get("name"))
        for lbl in pr_data.get("labels") or []
        if isinstance(lbl, dict) and isinstance(lbl.get("name"), str)
    ]
    raw_url = pr_data.get("url")
    url = raw_url.strip() if isinstance(raw_url, str) and raw_url.strip() else None

    body_title = (
        f"PR body for {candidate.label}"
        if candidate.pr_number is not None
        else f"Commit body for {candidate.commit_hash[:12]}"
    )
    write_text(
        candidate_dir / "body.md",
        render_text_block(body_title, str(pr_data.get("body") or ""), "No PR body available."),
    )
    write_text(
        candidate_dir / "comments.md",
        render_discussion_markdown(
            f"Comments for {candidate.label}", pr_data.get("comments") or [], "comments"
        ),
    )
    write_text(
        candidate_dir / "reviews.md",
        render_discussion_markdown(
            f"Reviews for {candidate.label}", pr_data.get("reviews") or [], "reviews"
        ),
    )

    reference_numbers = pr_refs[candidate.pr_number] if candidate.pr_number is not None else []
    linked_refs = _write_linked_refs(reference_numbers, bundle_dir, ref_data_map, live_refs)
    write_json(candidate_dir / "linked-issues.json", linked_refs)

    gh_files = list(pr_data.get("files") or [])
    manifest_entry = {
        "author": get_author_name(pr_data.get("author")) if pr_data else None,
        "bundle_dir": f"{candidate.bundle_group}/{candidate.bundle_name}",
        "commit_hash": candidate.commit_hash,
        "deprecated_added": candidate.deprecated_added,
        "deprecated_removed": candidate.deprecated_removed,
        "files": gh_files or candidate.files,
        "issue_refs": [item["number"] for item in linked_refs],
        "labels": labels,
        "pr": candidate.pr_number,
        "review_priority": candidate.review_priority,
        "subject": candidate.subject,
        "title": trim_pr_suffix(candidate.subject),
        "touches_src_main": candidate.touches_src_main,
        "url": url,
    }
    write_json(candidate_dir / "meta.json", manifest_entry)

    # If the fetch changed the commit hash (e.g. PR was backported/rebased),
    # any prior decision.json is stale — drop it so classify.py reruns.
    for aux in (
        "decision.json",
        "decision.md",
        "prompt.md",
        "cli-response.jsonl",
        "cli-response.txt",
    ):
        aux_path = candidate_dir / aux
        if aux_path.exists():
            aux_path.unlink()

    return manifest_entry


def _write_linked_refs(
    reference_numbers: list[int],
    bundle_dir: Path,
    ref_data_map: dict[int, dict[str, Any]],
    live_refs: set[int],
) -> list[dict[str, Any]]:
    linked_refs: list[dict[str, Any]] = []
    for ref_number in reference_numbers:
        live_refs.add(ref_number)
        ref_dir = bundle_dir / "refs" / str(ref_number)
        ref_dir.mkdir(parents=True, exist_ok=True)

        ref_data = ref_data_map.get(ref_number)
        if ref_data is None:
            # Reusing an existing ref bundle; pull title from disk.
            existing_ref_meta = _load_existing_meta(ref_dir / "meta.json") or {}
            title = str(existing_ref_meta.get("title") or f"Reference #{ref_number}")
        else:
            ref_meta: dict[str, Any] = {
                "number": ref_number,
                "bundle_dir": f"refs/{ref_number}",
                **ref_data,
            }
            title = str(ref_data.get("title") or f"Reference #{ref_number}")
            write_json(ref_dir / "meta.json", ref_meta)
            write_text(
                ref_dir / "body.md",
                render_text_block(
                    title,
                    str(ref_meta.get("body") or ""),
                    "No reference body available.",
                ),
            )
            write_text(
                ref_dir / "comments.md",
                render_discussion_markdown(
                    f"Comments for reference #{ref_number}",
                    ref_data.get("comments"),
                    "comments",
                ),
            )

        linked_refs.append({
            "number": ref_number,
            "bundle_dir": f"refs/{ref_number}",
            "title": title,
        })
    return linked_refs


def _prune_stale_dirs(
    bundle_dir: Path, candidates: list[Candidate], live_refs: set[int]
) -> None:
    # Prune PR/commit dirs no longer in the current candidate set.
    current_dirs: dict[str, set[str]] = {"prs": set(), "commits": set()}
    for c in candidates:
        current_dirs[c.bundle_group].add(c.bundle_name)
    for group, live in current_dirs.items():
        group_root = bundle_dir / group
        if not group_root.is_dir():
            continue
        for entry in group_root.iterdir():
            if entry.is_dir() and entry.name not in live:
                warn(f"Pruning stale {group}/{entry.name}")
                shutil.rmtree(entry)

    # Prune refs no longer referenced by any current PR.
    refs_root = bundle_dir / "refs"
    if refs_root.is_dir():
        for entry in refs_root.iterdir():
            if not entry.is_dir() or not entry.name.isdigit():
                continue
            if int(entry.name) not in live_refs:
                warn(f"Pruning stale refs/{entry.name}")
                shutil.rmtree(entry)


def _append_index_entry(
    index_lines: list[str], candidate: Candidate, manifest_entry: dict[str, Any]
) -> None:
    title = manifest_entry.get("title") or trim_pr_suffix(candidate.subject)
    heading = (
        f"PR #{candidate.pr_number}"
        if candidate.pr_number is not None
        else f"Commit {candidate.commit_hash[:12]}"
    )
    bundle_ref = f"{candidate.bundle_group}/{candidate.bundle_name}"
    index_lines.extend([
        f"## {heading}: {title}",
        "",
        f"- Bundle dir: `{manifest_entry.get('bundle_dir', bundle_ref)}`",
        f"- review_priority: `{candidate.review_priority}`",
        f"- touches_src_main: `{candidate.touches_src_main}`",
        f"- deprecated_added: `{candidate.deprecated_added}`",
        f"- deprecated_removed: `{candidate.deprecated_removed}`",
        f"- patch: [{bundle_ref}/patch.diff]({bundle_ref}/patch.diff)",
        f"- meta: [{bundle_ref}/meta.json]({bundle_ref}/meta.json)",
        f"- body: [{bundle_ref}/body.md]({bundle_ref}/body.md)",
        "",
    ])


def render_draft_output(
    breaking_prs: list[dict[str, Any]],
    deprecation_prs: list[dict[str, Any]],
    candidates: list[Candidate],
) -> str:
    lines = ["# Changelog", "", "## Unreleased", ""]
    lines.extend(render_labeled_prs("### ⚠️ Breaking changes to non-stable APIs", breaking_prs))
    lines.extend(render_labeled_prs("### 🚫 Deprecations", deprecation_prs))

    lines.extend(
        [
            "### 🌟 New javaagent instrumentation",
            "",
            "### 🌟 New library instrumentation",
            "",
            "### 📈 Enhancements",
            "",
            "### 🛠️ Bug fixes",
            "",
            "### 🧰 Tooling",
            "",
        ]
    )

    breaking_candidates = [candidate for candidate in candidates if candidate.deprecated_removed]
    if breaking_candidates:
        lines.extend(["#### Possible breaking changes (diff removes @Deprecated)", ""])
        for candidate in breaking_candidates:
            lines.append(f"- {format_subject_as_entry(candidate.subject)}")
        lines.append("")

    deprecation_candidates = [candidate for candidate in candidates if candidate.deprecated_added]
    if deprecation_candidates:
        lines.extend(["#### Possible deprecations (diff adds @Deprecated)", ""])
        for candidate in deprecation_candidates:
            lines.append(f"- {format_subject_as_entry(candidate.subject)}")
        lines.append("")

    src_main_candidates = [
        candidate
        for candidate in candidates
        if candidate.touches_src_main and not candidate.deprecated_added and not candidate.deprecated_removed
    ]
    if src_main_candidates:
        lines.extend(["#### Changes with src/main updates", ""])
        for candidate in src_main_candidates:
            lines.append(f"- {format_subject_as_entry(candidate.subject)}")
        lines.append("")

    no_src_main_candidates = [
        candidate
        for candidate in candidates
        if not candidate.touches_src_main and not candidate.deprecated_added and not candidate.deprecated_removed
    ]
    if no_src_main_candidates:
        lines.extend(["#### Changes without src/main updates", ""])
        for candidate in no_src_main_candidates:
            lines.append(f"- {format_subject_as_entry(candidate.subject)}")
        lines.append("")

    return "\n".join(lines) + "\n"


def do_draft(
    range_spec: str | None,
    bundle_dir: Path,
    skip_bundle: bool,
    refetch: bool = False,
) -> int:
    version = get_version()
    actual_range = range_spec or compute_default_range(version)
    candidates = build_candidates(actual_range)
    breaking_prs = fetch_labeled_prs(actual_range, "breaking change")
    deprecation_prs = fetch_labeled_prs(actual_range, "deprecation")

    if not skip_bundle:
        prepare_bundle(
            bundle_dir,
            version,
            actual_range,
            candidates,
            breaking_prs,
            deprecation_prs,
            refetch=refetch,
        )
        relative_bundle = bundle_dir.relative_to(REPO_ROOT).as_posix()
        warn(f"Prepared changelog bundle at {relative_bundle}")

    sys.stdout.write(render_draft_output(breaking_prs, deprecation_prs, candidates))
    return 0


def main() -> int:
    configure_standard_streams()
    args = parse_args()
    return do_draft(
        args.range,
        DEFAULT_BUNDLE_DIR,
        args.skip_bundle,
        refetch=args.refetch,
    )


if __name__ == "__main__":
    sys.exit(main())
