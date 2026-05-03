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
Idempotent: skips PRs whose decision.json already exists unless --force.
"""

from __future__ import annotations

import argparse
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
# full prompt would exceed MAX_PROMPT_CHARS.
MAX_DIFF_CHARS = 20_000
# Hard cap on total prompt length. Windows CreateProcess rejects command
# lines longer than 32767 wide chars, and copilot has no stdin/@file prompt
# input, so the entire prompt must fit in a single argv token. We leave
# headroom for the copilot.exe path, flags, and argv quoting overhead.
MAX_PROMPT_CHARS = 24_000


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


def preclassify(bundle: PrBundle) -> dict | None:
    """Return a decision dict if we can decide without the LLM, else None."""
    labels = bundle.meta.get("labels") or []
    if "automated code review" in labels:
        return {
            "decision": "omit",
            "section": None,
            "surface": "automated code review sweep",
            "user_visible_effect": "none",
            "bullet": None,
            "evidence": "PR labeled 'automated code review'",
            "source": "preclassify",
        }
    if not bundle.meta.get("touches_src_main"):
        files = [f["path"] for f in bundle.meta.get("files", [])]
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
    diff = bundle.diff
    truncated = False
    if len(diff) > MAX_DIFF_CHARS:
        diff = diff[:MAX_DIFF_CHARS] + "\n...[diff truncated for length]...\n"
        truncated = True
    files = bundle.meta.get("files", [])
    files_summary = "\n".join(
        f"  - {f['path']} (+{f.get('additions', 0)}/-{f.get('deletions', 0)})"
        for f in files[:50]
    )
    if len(files) > 50:
        files_summary += f"\n  ... and {len(files) - 50} more"
    if truncated:
        files_summary += "\n  (diff truncated; changed files list above is authoritative)"
    prompt = PROMPT_TEMPLATE.format(
        rules=rules,
        pr=bundle.pr,
        title=bundle.meta.get("title", ""),
        files_summary=files_summary,
        diff=diff,
    )
    # Enforce hard total cap. Oversized prompts come almost entirely from
    # pathological diffs (e.g. generated files, large snapshots); trim the
    # diff further and rebuild.
    if len(prompt) > MAX_PROMPT_CHARS:
        overshoot = len(prompt) - MAX_PROMPT_CHARS
        new_diff_len = max(1000, len(diff) - overshoot - 200)
        diff = diff[:new_diff_len] + "\n...[diff truncated for length]...\n"
        prompt = PROMPT_TEMPLATE.format(
            rules=rules,
            pr=bundle.pr,
            title=bundle.meta.get("title", ""),
            files_summary=files_summary,
            diff=diff,
        )
    return prompt


def invoke_cli(prompt_text: str, timeout: int) -> tuple[int, str, str]:
    """Run `copilot -p` with the prompt as a single argv token.

    --output-format json emits JSONL whose final `result` event carries
    premiumRequests, which we record in decision.json.
    --allow-all-tools is required in non-interactive mode.
    Model is overridable via $CLASSIFY_MODEL.
    """
    model = os.environ.get("CLASSIFY_MODEL", "gpt-5.4-mini")
    argv = [
        "copilot",
        "-p", prompt_text,
        "--output-format", "json",
        "--allow-all-tools",
        "--model", model,
    ]
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


def validate(decision: dict) -> list[str]:
    errors = []
    if decision.get("decision") not in ("include", "omit"):
        errors.append("decision must be include or omit")
    if decision.get("decision") == "include":
        if decision.get("section") not in VALID_SECTIONS - {None}:
            errors.append("section required for include")
        if not decision.get("bullet"):
            errors.append("bullet required for include")
    else:
        if decision.get("section") not in (None, "", "null"):
            errors.append("section must be null for omit")
    if not decision.get("evidence"):
        errors.append("evidence required")
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

    if decision_path.exists() and not args.force:
        return "skip", None, None

    # Preclassify first. Deterministic: path-pattern and metadata rules only.
    pre = preclassify(bundle)
    if pre is not None:
        pre["pr"] = bundle.pr
        decision_path.write_text(json.dumps(pre, indent=2), encoding="utf-8")
        md_path.write_text(render_markdown(bundle.pr, pre), encoding="utf-8")
        return f"pre:{pre['decision']}", None, pre

    if args.preclassify_only:
        return "needs-llm", None, None

    # Write prompt so it is inspectable alongside the decision.
    prompt = build_prompt(bundle, args.rules)
    prompt_path.write_text(prompt, encoding="utf-8")

    try:
        rc, out, err = invoke_cli(prompt, args.timeout)
    except subprocess.TimeoutExpired:
        return "error", f"timeout after {args.timeout}s", None
    if rc != 0:
        return "error", f"cli rc={rc}: {err.strip()[:500]}", None
    # Always persist the raw CLI stdout for forensic inspection, regardless
    # of format or success/failure. File extension reflects the content
    # format the copilot CLI returned.
    is_jsonl = out.lstrip().startswith('{"type":')
    raw_path = bundle.dir / ("cli-response.jsonl" if is_jsonl else "cli-response.txt")
    raw_path.write_text(out, encoding="utf-8")
    usage: dict | None = None
    response_text = out
    if is_jsonl:
        response_text, usage = parse_copilot_jsonl(out)
    try:
        decision = parse_response(response_text)
    except (json.JSONDecodeError, ValueError) as e:
        return "error", f"parse failure ({e}); raw saved to {raw_path}", None
    errs = validate(decision)
    if errs:
        return "error", "validation: " + "; ".join(errs) + f"; raw saved to {raw_path}", None
    decision["pr"] = bundle.pr
    decision.setdefault("source", "llm")
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
