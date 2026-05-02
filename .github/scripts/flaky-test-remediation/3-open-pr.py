#!/usr/bin/env python3
"""Render the PR body markdown and open the PR via ``gh``.

Reads ``build/flaky-test-remediation/selected.json`` and (optionally)
``build/flaky-test-remediation/diagnosis.md``; writes ``build/flaky-test-remediation/pr-body.md``;
then runs ``gh pr create``.

Required env: ``PR_HEAD`` (head ref, e.g. ``branch`` for same-repo or
``owner:branch`` for cross-repo PRs).
Optional env:
  ``PR_BASE`` (default ``main``)
  ``PR_REPO`` (passed to ``gh -R`` for cross-repo PRs; omit for same-repo)
  ``PR_DRAFT`` (truthy = open as draft)
  ``ARTIFACT_URL`` (appended as a "Download Copilot diagnostics" link)
  ``GH_TOKEN`` (consumed by ``gh`` directly)
"""

import json
import os
import re
import subprocess
import sys

from _paths import DIAGNOSIS, PR_BODY, SELECTED
from _render import utc_day


def pr_title_target(selected):
    class_name = selected["class"].rsplit(".", 1)[-1]
    method_name = re.sub(r"\[\d+\]$", "", selected["method"])
    return f"{class_name}.{method_name}"


def render(selected):
    fq = selected["fully_qualified"]
    source_file = selected["source_file"]
    window_days = selected["window_days"]
    sample_url = selected["sample_scan_url"]
    sample_failure = selected["sample_failure"].rstrip()

    lines = [
        f"Automated attempt at fixing flakiness in `{fq}`.",
        "",
        f"- Source: [`{source_file}`]({source_file})",
        f"- Flaky executions in last {window_days}d (this test): "
        f"**{selected['flaky_count']}**",
        f"- Flaky executions in last {window_days}d (test container): "
        f"**{selected['container_flaky_count']}**",
    ]
    if sample_url:
        lines.append(f"- Primary failed scan: {sample_url}")
    lines.append("")

    scans = selected["recent_flaky_scans"]
    if scans:
        lines += ["### Recent failed/flaky scans", ""]
        for s in scans[:5]:
            bullet = f"- [{s['build_id'][:13]}]({s['scan_url']}) ({s['outcome']}"
            if s["work_unit"]:
                bullet += f", `{s['work_unit']}`"
            bullet += ")"
            lines.append(bullet)
        lines.append("")

    rows = selected["per_day_breakdown"]
    if rows:
        lines += ["### Flake history (per UTC day)", "",
                  "| Day | flaky | failed | passed |",
                  "| --- | ---: | ---: | ---: |"]
        for r in rows:
            lines.append(f"| {utc_day(r['start_ms'])} | "
                         f"{r['flaky']} | {r['failed']} | {r['passed']} |")
        lines.append("")

    lines += ["### Sample failure (from Develocity)", "", "```",
              sample_failure or "(no failure message captured)",
              "```", ""]

    if DIAGNOSIS.exists():
        diagnosis_text = DIAGNOSIS.read_text(encoding="utf-8").strip()
        if diagnosis_text:
            lines += ["## Copilot diagnosis", "", diagnosis_text, ""]

    lines += [
        "---",
        "",
        "Review the diagnosis and the diff carefully before merging - "
        "automated fixes can mask flakiness instead of addressing the root cause.",
    ]
    return "\n".join(lines) + "\n"


def main():
    head = os.environ.get("PR_HEAD")
    if not head:
        print("error: PR_HEAD env var is required", file=sys.stderr)
        return 2
    base = os.environ.get("PR_BASE", "main")
    repo = os.environ.get("PR_REPO")
    draft = os.environ.get("PR_DRAFT", "").lower() in ("1", "true", "yes")
    artifact_url = os.environ.get("ARTIFACT_URL", "").strip()

    selected = json.loads(SELECTED.read_text(encoding="utf-8"))
    body = render(selected)
    if artifact_url:
        body += f"\n[Download Copilot diagnostics]({artifact_url})\n"
    PR_BODY.write_text(body, encoding="utf-8")
    print(f"Rendered PR body to {PR_BODY}")

    cmd = ["gh", "pr", "create",
           "--title", f"Reduce flakiness in {pr_title_target(selected)}",
           "--body-file", str(PR_BODY),
           "--base", base,
           "--head", head]
    if repo:
        cmd += ["-R", repo]
    if draft:
        cmd.append("--draft")
    print(f"Opening PR: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
