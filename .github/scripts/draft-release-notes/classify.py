#!/usr/bin/env python3
"""Classify each PR in build/changelog-bundle/prs/ into a CHANGELOG section.

For every PR bundle produced by .github/scripts/draft-release-notes/fetch.py,
this script writes a per-PR decision artifact. The artifact forces a one-PR-
at-a-time diff-based decision before any CHANGELOG text is written, which
is the design intent of the draft-release-notes skill.

Outputs per PR (under build/changelog-bundle/prs/<N>/):
  - prompt.md                 — LLM prompt with the diff embedded
  - decision.json             — structured classification (schema below)
  - decision.md               — human-readable rendering
  - cli-response.jsonl / .txt — raw copilot stdout (forensic; always written
                               on non-preclassify runs regardless of outcome)

decision.json schema:
  {
    "pr": <int>,
    "decision": "include" | "omit",
    "section": "breaking" | "deprecations" | "new-javaagent"
             | "new-library" | "enhancements" | "bug-fixes" | null,
    "surface": <short phrase>,
    "user_visible_effect": <one sentence or "none">,
    "bullet": <final CHANGELOG sentence without PR link> | null,
    "evidence": <2-4 line verbatim quote from the diff>,
    "source": "preclassify" | "llm"
  }

Invokes `copilot` (must be on PATH) per PR. Response is expected on stdout
as a JSON object matching the schema above (markdown code fences tolerated).
Model is overridable via $CLASSIFY_MODEL (default: gpt-5.4-mini).

Run with --jobs N for parallelism (default 4).
Idempotent: reuses decisions whose classifier fingerprint still matches unless
--force is supplied.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path

BUNDLE_ROOT = Path("build/changelog-bundle/prs")
RULES_PATH = Path(__file__).resolve().parent / "rules.md"
# Initial diff cap. The build_prompt() function further trims the diff if the
# full prompt would exceed MAX_PROMPT_UTF16_UNITS.
MAX_DIFF_CHARS = 20_000
# Hard cap on total prompt length. Windows CreateProcess rejects command
# lines longer than 32767 UTF-16 code units, and copilot has no stdin/@file
# prompt input. This leaves headroom for flags, quoting, and retry guidance.
MAX_PROMPT_UTF16_UNITS = 24_000
WINDOWS_COMMAND_LINE_UTF16_LIMIT = 32_767
MAX_LLM_ATTEMPTS = 2
IMPORTANT_EXCERPT_CONTEXT = 8
CLASSIFIER_VERSION = 1


VALID_SECTIONS = {
    "breaking",
    "deprecations",
    "new-javaagent",
    "new-library",
    "enhancements",
    "bug-fixes",
    None,
}

PROMPT_TEMPLATE = """You are classifying a single PR from the \
opentelemetry-java-instrumentation repository for inclusion in CHANGELOG.md.

Apply the classification rules below. Respond with a single JSON object \
matching the schema described in those rules and nothing else (no prose, \
no code fences).

---BEGIN RULES---
{rules}
---END RULES---

PR number: {pr}
Title (for link bookkeeping only, not evidence): {title}

Changed files:
{files_summary}

Release context:
{release_context}

---BEGIN DIFF---
{diff}
---END DIFF---
"""


def load_rules() -> str:
    try:
        return RULES_PATH.read_text(encoding="utf-8")
    except FileNotFoundError:
        sys.exit(f"rules file not found: {RULES_PATH}")


@dataclass
class PrBundle:
    pr: int
    dir: Path
    meta: dict
    diff: str


def classifier_fingerprint(bundle: PrBundle, rules: str) -> str:
    digest = hashlib.sha256()
    digest.update(str(CLASSIFIER_VERSION).encode("ascii"))
    digest.update(effective_model().encode("utf-8"))
    digest.update(build_prompt(bundle, rules).encode("utf-8"))
    digest.update(
        json.dumps(bundle.meta, sort_keys=True, separators=(",", ":")).encode("utf-8")
    )
    return digest.hexdigest()


def iter_bundles() -> list[PrBundle]:
    if not BUNDLE_ROOT.is_dir():
        sys.exit(f"{BUNDLE_ROOT} not found; run .github/scripts/draft-release-notes/fetch.py first")
    out = []
    for d in sorted(BUNDLE_ROOT.iterdir(), key=lambda p: int(p.name) if p.name.isdigit() else 0):
        if not d.is_dir() or not d.name.isdigit():
            continue
        meta_path = d / "meta.json"
        diff_path = d / "patch.diff"
        if not meta_path.exists() or not diff_path.exists():
            continue
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        diff = diff_path.read_text(encoding="utf-8", errors="replace")
        out.append(PrBundle(pr=int(d.name), dir=d, meta=meta, diff=diff))
    return out


# --- preclassifier ---------------------------------------------------------


def changed_paths(bundle: PrBundle) -> list[str]:
    paths = []
    for item in bundle.meta.get("files", []):
        if isinstance(item, dict):
            path = item.get("path")
        else:
            path = item
        if isinstance(path, str):
            paths.append(path)
    return paths


def preclassify(bundle: PrBundle) -> dict | None:
    """Return a decision dict if we can decide without the LLM, else None."""
    labels = bundle.meta.get("labels") or []
    if "module cleanup" in labels:
        return {
            "decision": "omit",
            "section": None,
            "surface": "module cleanup",
            "user_visible_effect": "none",
            "bullet": None,
            "evidence": "PR labeled 'module cleanup'",
            "source": "preclassify",
        }
    files = changed_paths(bundle)
    if not bundle.meta.get("touches_src_main"):
        return {
            "decision": "omit",
            "section": None,
            "surface": "test/build/docs only",
            "user_visible_effect": "none",
            "bullet": None,
            "evidence": "no changed paths are user-facing /src/main/: "
            + ", ".join(files[:5])
            + ("..." if len(files) > 5 else ""),
            "source": "preclassify",
        }
    return None


# --- prompt + invocation ---------------------------------------------------

def build_prompt(bundle: PrBundle, rules: str) -> str:
    diff = strip_changelog_diff(bundle.diff)
    files = bundle.meta.get("files", [])
    file_lines = []
    for item in files[:50]:
        if isinstance(item, dict):
            path = item.get("path")
            if isinstance(path, str):
                file_lines.append(
                    f"  - {path} (+{item.get('additions', 0)}/-{item.get('deletions', 0)})"
                )
        elif isinstance(item, str):
            file_lines.append(f"  - {item}")
    files_summary = "\n".join(file_lines)
    if len(files) > 50:
        files_summary += f"\n  ... and {len(files) - 50} more"
    release_context = release_context_for(diff, bundle.meta.get("deprecated_added", False))
    prompt_without_diff = PROMPT_TEMPLATE.format(
        rules=rules,
        pr=bundle.pr,
        title=bundle.meta.get("title", ""),
        files_summary=files_summary,
        release_context=release_context,
        diff="",
    )
    base_units = utf16_units(prompt_without_diff)
    diff_budget = min(MAX_DIFF_CHARS, MAX_PROMPT_UTF16_UNITS - base_units - 200)
    if diff_budget < 0:
        raise RuntimeError("classification rules and PR metadata exceed the prompt size limit")
    while diff_budget >= 0:
        compacted_diff = compact_diff(diff, diff_budget)
        prompt = PROMPT_TEMPLATE.format(
            rules=rules,
            pr=bundle.pr,
            title=bundle.meta.get("title", ""),
            files_summary=files_summary,
            release_context=release_context,
            diff=compacted_diff,
        )
        excess = utf16_units(prompt) - MAX_PROMPT_UTF16_UNITS
        if excess <= 0:
            return prompt
        diff_budget -= excess + 100
    raise RuntimeError("classification prompt exceeds the prompt size limit")


def strip_changelog_diff(diff: str) -> str:
    sections = re.split(r"(?=^diff --git )", diff, flags=re.MULTILINE)
    return "".join(
        section
        for section in sections
        if section.startswith("diff --git ")
        and not section.startswith("diff --git a/CHANGELOG.md b/CHANGELOG.md")
    )


def compact_diff(diff: str, max_chars: int) -> str:
    if max_chars <= 0:
        return ""
    if len(diff) <= max_chars:
        return diff

    lines = diff.splitlines()
    priority_patterns = (
        re.compile(r"^\+.*(?:@Deprecated\b|@deprecated\b)", re.IGNORECASE),
        re.compile(r"^[+-]\s*\+\+\+\s+NEW (?:METHOD|CLASS):"),
        re.compile(r"^\+.*\bdeprecated\b", re.IGNORECASE),
    )
    indexes = []
    seen = set()
    for pattern in priority_patterns:
        for index, line in enumerate(lines):
            if index not in seen and pattern.search(line):
                indexes.append(index)
                seen.add(index)

    excerpt_lines = []
    covered = set()
    excerpt_budget = max_chars // 2
    for index in indexes:
        start = max(0, index - IMPORTANT_EXCERPT_CONTEXT)
        end = min(len(lines), index + IMPORTANT_EXCERPT_CONTEXT + 1)
        chunk = [
            lines[line_index]
            for line_index in range(start, end)
            if line_index not in covered
        ]
        if not chunk:
            continue
        rendered = "\n".join(chunk) + "\n"
        if len("\n".join(excerpt_lines)) + len(rendered) > excerpt_budget:
            break
        excerpt_lines.extend(chunk)
        covered.update(range(start, end))

    excerpt = "\n".join(excerpt_lines)
    marker = "\n...[diff truncated; important excerpts follow]...\n"
    if max_chars <= len(marker):
        return diff[:max_chars]
    head_budget = max(0, max_chars - len(marker) - len(excerpt))
    return diff[:head_budget] + marker + excerpt


def release_context_for(diff: str, deprecated_added: bool = False) -> str:
    context = []
    removed_new_api = re.search(
        r"^-\s*\+\+\+\s+NEW (?:METHOD|CLASS):",
        diff,
        re.MULTILINE,
    )
    added_new_api = re.search(
        r"^\+\s*\+\+\+\s+NEW (?:METHOD|CLASS):",
        diff,
        re.MULTILINE,
    )
    if removed_new_api and added_new_api:
        context.append(
            "The API-diff snapshot marks both the old and new signatures as NEW relative to the "
            "previous release. If changing that unreleased API is the PR's only user-visible effect, "
            "omit the PR."
        )
    if deprecated_added or adds_deprecation_notice(diff):
        context.append(
            "The source or documentation diff adds a deprecation declaration or notice. If it "
            "introduces a user-facing deprecation, use the Deprecations section even when the PR "
            "also adds its replacement. Do not treat references to an already-deprecated surface "
            "or internal compatibility helpers as a new deprecation."
        )
    experimental_methods = deprecated_experimental_methods(diff)
    if experimental_methods:
        context.append(
            "The PR newly deprecates these published Experimental helper methods, which must be "
            "named in the deprecation bullet: "
            + ", ".join(f"`{method}(...)`" for method in experimental_methods)
            + "."
        )
        property_migrations = deprecated_property_migrations(diff)
        if property_migrations:
            context.append(
                "The same bullet must also name these configuration migrations: "
                + ", ".join(
                    f"`{old}` to `{new}`" for old, new in property_migrations
                )
                + "."
            )
    return " ".join(context) or "No additional release-baseline facts."


def utf16_units(text: str) -> int:
    return len(text.encode("utf-16-le")) // 2


def effective_model() -> str:
    return os.environ.get("CLASSIFY_MODEL", "gpt-5.4-mini")


def command_line_utf16_units(argv: list[str]) -> int:
    return utf16_units(subprocess.list2cmdline(argv))


def invoke_cli(prompt_text: str, timeout: int) -> tuple[int, str, str]:
    """Run `copilot -p` with the prompt as a single argv token.

    --output-format json emits JSONL whose final `result` event carries
    premiumRequests, which we record in decision.json.
    --allow-all-tools is required in non-interactive mode.
    Model is overridable via $CLASSIFY_MODEL.
    """
    argv = [
        "copilot",
        "-p", prompt_text,
        "--output-format", "json",
        "--allow-all-tools",
        "--model", effective_model(),
    ]
    if command_line_utf16_units(argv) >= WINDOWS_COMMAND_LINE_UTF16_LIMIT:
        return 1, "", "command line exceeds the Windows CreateProcess limit"
    proc = subprocess.run(
        argv,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=timeout,
    )
    return proc.returncode, proc.stdout, proc.stderr


def parse_copilot_jsonl(s: str) -> tuple[str, dict]:
    """Extract concatenated assistant message text and usage from copilot JSONL.

    Returns (response_text, usage) where usage is:
      {"premium_requests": <int or None>}
    """
    parts: list[str] = []
    premium_requests: int | None = None
    for line in s.splitlines():
        line = line.strip()
        if not line or not line.startswith("{"):
            continue
        try:
            evt = json.loads(line)
        except json.JSONDecodeError:
            continue
        et = evt.get("type")
        data = evt.get("data") or {}
        if et == "assistant.message":
            content = data.get("content")
            if isinstance(content, str):
                parts.append(content)
        elif et == "result":
            usage = evt.get("usage") or {}
            if isinstance(usage.get("premiumRequests"), int):
                premium_requests = usage["premiumRequests"]
    return "\n".join(parts), {"premium_requests": premium_requests}


def parse_response(s: str) -> dict:
    s = s.strip()
    s = re.sub(r"^```(?:json)?\s*", "", s, flags=re.I)
    s = re.sub(r"\s*```$", "", s)
    # The model sometimes emits scratchpad objects (e.g. {"intent": "..."})
    # before the real decision object. Walk all top-level JSON objects in
    # the string and return the last one that has a "decision" key, falling
    # back to the last object if none match.
    decoder = json.JSONDecoder()
    objects: list[dict] = []
    i = 0
    n = len(s)
    while i < n:
        # Skip to the next object start.
        j = s.find("{", i)
        if j == -1:
            break
        try:
            obj, end = decoder.raw_decode(s, j)
        except json.JSONDecodeError:
            i = j + 1
            continue
        if isinstance(obj, dict):
            objects.append(obj)
        i = end
    if not objects:
        # Force the original error path for callers that expect JSONDecodeError.
        return json.loads(s)
    for obj in reversed(objects):
        if "decision" in obj:
            return obj
    return objects[-1]


def adds_deprecation_notice(diff: str) -> bool:
    current_path = ""
    for line in diff.splitlines():
        path_match = re.match(r"^diff --git a/(.+) b/(.+)$", line)
        if path_match is not None:
            current_path = path_match.group(2)
            continue
        if (
            current_path == "CHANGELOG.md"
            or "/src/test" in current_path
            or not line.startswith("+")
            or line.startswith("+++")
        ):
            continue
        added_line = line[1:]
        if re.search(
            r"@Deprecated\b|@deprecated\b|\bis deprecated\b|"
            r"\bdeprecated (?:and|in|property|option)\b|Deprecated:",
            added_line,
            re.IGNORECASE,
        ):
            return True
    return False


def deprecated_experimental_methods(diff: str) -> list[str]:
    lines = diff.splitlines()
    current_path = ""
    methods = []
    for index, line in enumerate(lines):
        path_match = re.match(r"^diff --git a/(.+) b/(.+)$", line)
        if path_match is not None:
            current_path = path_match.group(2)
            continue
        if not current_path.endswith("/internal/Experimental.java"):
            continue
        if not line.startswith("+") or not re.search(
            r"@Deprecated\b|@deprecated\b", line, re.IGNORECASE
        ):
            continue
        for candidate in lines[index + 1 : index + 16]:
            declaration = re.search(
                r"\b(?:public|protected)\s+(?:static\s+)?[\w<>, ?.@\[\]]+\s+(\w+)\s*\(",
                candidate,
            )
            if declaration is not None:
                method = declaration.group(1)
                if method not in methods:
                    methods.append(method)
                break
    return methods


def deprecated_property_migrations(diff: str) -> list[tuple[str, str]]:
    lines = diff.splitlines()
    migrations = []
    property_pattern = re.compile(r"(otel\.[a-z0-9_.-]+)")
    for index, line in enumerate(lines):
        if not line.startswith("+") or "Deprecated: use `" not in line:
            continue
        replacement_match = re.search(r"Deprecated: use `(otel\.[a-z0-9_.-]+)`", line)
        if replacement_match is None:
            continue
        old_property = None
        for previous in reversed(lines[max(0, index - 10) : index]):
            candidates = property_pattern.findall(previous)
            if candidates:
                old_property = candidates[-1]
                break
        if old_property is not None:
            migration = (old_property, replacement_match.group(1))
            if migration not in migrations:
                migrations.append(migration)
    return migrations


def validate(decision: dict, bundle: PrBundle | None = None) -> list[str]:
    errors = []
    decision_value = decision.get("decision")
    section = decision.get("section")
    bullet_value = decision.get("bullet")
    evidence = decision.get("evidence")
    if not isinstance(decision_value, str) or decision_value not in ("include", "omit"):
        errors.append("decision must be include or omit")
    if decision_value == "include":
        if not isinstance(section, str) or section not in VALID_SECTIONS - {None}:
            errors.append("section required for include")
        if not isinstance(bullet_value, str) or not bullet_value.strip():
            errors.append("bullet required for include")
    else:
        if section not in (None, "", "null"):
            errors.append("section must be null for omit")
        if bullet_value is not None:
            errors.append("bullet must be null for omit")
    for field in ("surface", "user_visible_effect"):
        if not isinstance(decision.get(field), str):
            errors.append(f"{field} must be a string")
    if not isinstance(evidence, str) or not evidence.strip():
        errors.append("evidence required")
    bullet = bullet_value if isinstance(bullet_value, str) else ""
    if "\n" in bullet:
        errors.append("bullet must be a single line without a PR link")
    if re.search(r"https://github\.com/.+/pull/\d+", bullet):
        errors.append("bullet must not include a PR link")
    if bundle is not None and decision_value == "include":
        diff = strip_changelog_diff(bundle.diff)
        experimental_methods = deprecated_experimental_methods(diff)
        for method in experimental_methods:
            if method not in bullet:
                errors.append(f"deprecation bullet must name `{method}(...)`")
        if experimental_methods:
            for old_property, replacement in deprecated_property_migrations(diff):
                for property_name in (old_property, replacement):
                    if property_name not in bullet:
                        errors.append(
                            f"deprecation bullet must name `{property_name}`"
                        )
    if "otel.semconv-stability.preview" in bullet:
        errors.append("bullet must use the public otel.semconv-stability.opt-in property")
    if decision_value == "include" and section == "deprecations":
        if not bullet.startswith("Deprecate "):
            errors.append("deprecation bullet must start with 'Deprecate '")
        if " in favor of " not in bullet:
            errors.append("deprecation bullet must use 'in favor of'")
        lower_bullet = bullet.lower()
        forbidden = (
            "declarative instrumentation configuration",
            "the deprecated ",
            "may be removed",
            "removable in",
            "the new ",
        )
        for phrase in forbidden:
            if phrase in lower_bullet:
                errors.append(f'deprecation bullet must omit "{phrase}"')
    return errors


def render_markdown(pr: int, decision: dict) -> str:
    lines = [
        f"# PR #{pr}",
        "",
        f"- decision: **{decision.get('decision')}**",
        f"- section: {decision.get('section')}",
        f"- source: {decision.get('source', 'llm')}",
        f"- surface: {decision.get('surface')}",
        f"- user-visible effect: {decision.get('user_visible_effect')}",
        "",
        "## bullet",
        "",
        decision.get("bullet") or "_(none)_",
        "",
        "## evidence",
        "",
        "```",
        (decision.get("evidence") or "").strip(),
        "```",
        "",
    ]
    return "\n".join(lines)


# --- main ------------------------------------------------------------------


def process_one(bundle: PrBundle, args) -> tuple[str, str | None, dict | None]:
    """Classify one PR. Returns (status, error_or_None, decision_or_None)."""
    decision_path = bundle.dir / "decision.json"
    md_path = bundle.dir / "decision.md"
    prompt_path = bundle.dir / "prompt.md"
    fingerprint = classifier_fingerprint(bundle, args.rules)

    if decision_path.exists() and not args.force:
        try:
            existing = json.loads(decision_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            existing = {}
        if (
            existing.get("classifier_fingerprint") == fingerprint
            and not validate(existing, bundle)
        ):
            return "skip", None, None

    # Preclassify first. Deterministic: path-pattern and metadata rules only.
    pre = preclassify(bundle)
    if pre is not None:
        pre["pr"] = bundle.pr
        pre["classifier_fingerprint"] = fingerprint
        decision_path.write_text(json.dumps(pre, indent=2), encoding="utf-8")
        md_path.write_text(render_markdown(bundle.pr, pre), encoding="utf-8")
        return f"pre:{pre['decision']}", None, pre

    if args.preclassify_only:
        return "needs-llm", None, None

    # Write prompt so it is inspectable alongside the decision.
    prompt = build_prompt(bundle, args.rules)
    prompt_path.write_text(prompt, encoding="utf-8")

    retry_prompt = prompt
    premium_requests = 0
    decision: dict | None = None
    usage: dict | None = None
    last_error = ""
    raw_path = bundle.dir / "cli-response.txt"
    for attempt in range(1, MAX_LLM_ATTEMPTS + 1):
        try:
            rc, out, err = invoke_cli(retry_prompt, args.timeout)
        except subprocess.TimeoutExpired:
            last_error = f"timeout after {args.timeout}s"
            continue
        if rc != 0:
            last_error = f"cli rc={rc}: {err.strip()[:500]}"
            continue
        is_jsonl = out.lstrip().startswith('{"type":')
        suffix = "jsonl" if is_jsonl else "txt"
        name = f"cli-response.{suffix}" if attempt == 1 else f"cli-response-retry-{attempt}.{suffix}"
        raw_path = bundle.dir / name
        raw_path.write_text(out, encoding="utf-8")
        response_text = out
        if is_jsonl:
            response_text, attempt_usage = parse_copilot_jsonl(out)
            value = attempt_usage.get("premium_requests")
            if isinstance(value, int):
                premium_requests += value
        try:
            candidate = parse_response(response_text)
        except (json.JSONDecodeError, ValueError) as e:
            last_error = f"parse failure ({e})"
        else:
            errs = validate(candidate, bundle)
            if not errs:
                decision = candidate
                usage = {"premium_requests": premium_requests}
                break
            last_error = "validation: " + "; ".join(errs)
        retry_prompt = (
            prompt
            + "\n\nThe previous response failed validation: "
            + last_error
            + "\nReturn a corrected single JSON object that follows every rule."
        )
    if decision is None:
        return "error", f"{last_error}; raw saved to {raw_path}", None
    decision["pr"] = bundle.pr
    decision.setdefault("source", "llm")
    decision["classifier_fingerprint"] = fingerprint
    if usage is not None:
        decision["usage"] = usage
    decision_path.write_text(json.dumps(decision, indent=2), encoding="utf-8")
    md_path.write_text(render_markdown(bundle.pr, decision), encoding="utf-8")
    return f"llm:{decision['decision']}", None, decision


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jobs", type=int, default=4, help="parallel CLI invocations (default 4)")
    ap.add_argument("--timeout", type=int, default=900, help="per-PR CLI timeout seconds")
    ap.add_argument("--force", action="store_true", help="re-classify PRs with existing decision.json")
    ap.add_argument("--only", type=int, nargs="*", help="restrict to these PR numbers")
    ap.add_argument(
        "--preclassify-only",
        action="store_true",
        help="Run deterministic preclassifier only; skip LLM calls. "
        "PRs that need LLM classification are reported but left without a decision.json.",
    )
    args = ap.parse_args()
    args.rules = "" if args.preclassify_only else load_rules()

    bundles = iter_bundles()
    if args.only:
        wanted = set(args.only)
        bundles = [b for b in bundles if b.pr in wanted]
    if not bundles:
        print("No PR bundles to process.")
        return 0

    counts: dict[str, int] = {}
    errors: list[str] = []
    premium_requests = 0
    prs_with_usage = 0
    total = len(bundles)

    with ThreadPoolExecutor(max_workers=max(1, args.jobs)) as ex:
        futures = {ex.submit(process_one, b, args): b for b in bundles}
        for done, fut in enumerate(as_completed(futures), start=1):
            bundle = futures[fut]
            status, err, decision = fut.result()
            counts[status] = counts.get(status, 0) + 1
            if err:
                errors.append(f"#{bundle.pr}: {err}")
                print(f"[{done}/{total}] #{bundle.pr}: ERROR {err}", file=sys.stderr)
                continue
            print(f"[{done}/{total}] #{bundle.pr}: {status}")
            usage = (decision or {}).get("usage")
            if isinstance(usage, dict):
                prs_with_usage += 1
                v = usage.get("premium_requests")
                if isinstance(v, int):
                    premium_requests += v

    print()
    print("Summary:")
    for k, v in sorted(counts.items()):
        print(f"  {k}: {v}")
    if prs_with_usage:
        print()
        print("LLM usage (from copilot --output-format json):")
        print(f"  PRs with usage data: {prs_with_usage}")
        print(f"  premium requests:    {premium_requests}")
    if errors:
        print(f"\n{len(errors)} errors; rerun with --force on those PRs after fixing.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
