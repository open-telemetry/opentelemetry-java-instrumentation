#!/usr/bin/env python3
"""Render the Copilot prompt and invoke the Copilot CLI agent.

Reads ``build/flaky-fix/selected.json``, writes ``build/flaky-fix/prompt.txt``,
then runs ``copilot -p ...`` capturing stdout/stderr to
``build/flaky-fix/copilot-output.jsonl`` and ``copilot-stderr.log``.

Exits with the Copilot CLI's exit code so the caller can decide whether to
upload diagnostics and gate downstream steps.

Required env: ``COPILOT_GITHUB_TOKEN`` (set by the workflow).
Optional env: ``MODEL`` (defaults to ``gpt-5.5``).
"""

import json
import os
import subprocess
import sys

from _paths import COPILOT_ERR, COPILOT_LOG, PROMPT, SELECTED
from _render import utc_day

DEFAULT_MODEL = "gpt-5.5"

PROMPT_TEMPLATE = """\
You are fixing flakiness in a single JUnit test in the
opentelemetry-java-instrumentation repository. Read repository conventions in
`.github/copilot-instructions.md`, `AGENTS.md`, and `CONTRIBUTING.md` before
making changes.

Target test: `{fq}`
Source file: `{source}`
Window analyzed: last {window_days} days
Flaky executions of this method in window: {flaky_count}
Flaky executions of the container in same window: {container_flaky_count}

Sample failed build scan: {scan_url}

Sample failure captured from Develocity (truncated):
```
{failure}
```

{recent_section}{per_day_section}
Goals:
1. Diagnose the root cause of flakiness from the sample failure(s) and the
   test source. Common causes include unbounded waits, fixed-duration sleeps,
   shared mutable state across tests, executor/IO-reactor reuse, missing
   cleanup, and gRPC `Context` cancellation propagating into deferred work.
   Look at related helper files referenced from the test (such as
   `AbstractGrpcTest.java` for gRPC tests).
2. If multiple scan links are listed above, open at least two of them via
   their build-scan URLs to confirm the failure mode is consistent before
   committing to a fix.
3. Apply a minimal, surgical fix to the test or its helper. Do NOT rewrite
   unrelated code. Do NOT add new dependencies.
4. Preserve the test's original intent and assertions. If the test was
   regression-tracking a specific issue, keep that coverage.
5. If the fix needs `await` calls, prefer assertions on the boolean return
   value of `CountDownLatch.await(...)` over ignoring it; prefer Awaitility
   helpers already used elsewhere in the test for polling.
6. Do not disable the test, do not add `@Disabled`, and do not lower coverage
   by deleting assertions.

Output protocol:
- Apply the fix to the working tree (do not produce JSON output).
- After applying the fix, write a Markdown diagnosis to
  `build/flaky-fix/diagnosis.md` (path relative to the repo root) with these
  sections, in this order:
  1. `## Root cause` - 2-4 sentences explaining what caused the flake.
  2. `## Fix` - bullet list summarizing the code changes you made.
  3. `## Why this addresses the root cause` - 2-3 sentences.
  4. `## Risks / follow-ups` - 1-3 bullets covering anything that could
     surface a new failure mode (e.g. legitimate timeouts that were
     previously masked) or that a maintainer should validate.
- Do NOT add the diagnosis file to git; the driver script will collect it.
"""


def render_prompt(selected):
    failure = (selected["sample_failure"]
               or "(no failure message captured)").strip()
    if len(failure) > 4000:
        failure = failure[:4000] + "\n... [truncated]"

    recent = ""
    scans = selected["recent_flaky_scans"]
    if scans:
        lines = ["Other recent flaky/failed scans for this test:"]
        for s in scans[:5]:
            bullet = f"- {s['scan_url']} ({s['outcome']}"
            if s["work_unit"]:
                bullet += f", {s['work_unit']}"
            bullet += ")"
            excerpt = s["failure_excerpt"].splitlines()
            if excerpt:
                bullet += f"\n    first line: `{excerpt[0][:160]}`"
            lines.append(bullet)
        lines.append("")
        recent = "\n".join(lines) + "\n"

    per_day = ""
    rows = selected["per_day_breakdown"]
    if rows:
        lines = ["Per-day outcome breakdown for this test:", "",
                 "| Day (UTC) | flaky | failed | passed |",
                 "| --- | ---: | ---: | ---: |"]
        for r in rows:
            lines.append(f"| {utc_day(r['start_ms'])} | "
                         f"{r['flaky']} | {r['failed']} | {r['passed']} |")
        lines.append("")
        per_day = "\n".join(lines) + "\n"

    return PROMPT_TEMPLATE.format(
        fq=selected["fully_qualified"],
        source=selected["source_file"],
        window_days=selected["window_days"],
        flaky_count=selected["flaky_count"],
        container_flaky_count=selected["container_flaky_count"],
        scan_url=selected["sample_scan_url"] or "(none)",
        failure=failure,
        recent_section=recent,
        per_day_section=per_day,
    )


def main():
    model = os.environ.get("MODEL", DEFAULT_MODEL)

    selected = json.loads(SELECTED.read_text(encoding="utf-8"))
    PROMPT.write_text(render_prompt(selected), encoding="utf-8")
    print(f"Rendered prompt to {PROMPT}")

    print(f"Running Copilot CLI (model={model})")
    # `--yolo` disables Copilot CLI confirmation prompts. Required for
    # autonomous CI; do not copy into interactive scripts.
    with open(COPILOT_LOG, "wb") as out, open(COPILOT_ERR, "wb") as err:
        rc = subprocess.run(
            ["copilot", "-p", PROMPT.read_text(encoding="utf-8"),
             "--model", model,
             "--output-format", "json",
             "--silent",
             "--stream", "off",
             "--yolo"],
            stdout=out, stderr=err, check=False,
        ).returncode
    print(f"copilot exited rc={rc} (log: {COPILOT_LOG})")
    return rc


if __name__ == "__main__":
    sys.exit(main())
