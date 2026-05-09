"""Shared paths for the flaky-test-remediation toolkit.

All intermediate files live under ``build/flaky-test-remediation/`` (gitignored). The
workflow and ``run-local.py`` both rely on this layout; do not change a path
here without also updating both.
"""

from pathlib import Path

OUT_DIR = Path("build/flaky-test-remediation")
SKIP = OUT_DIR / "skip.txt"
SELECTED = OUT_DIR / "selected.json"
PROMPT = OUT_DIR / "prompt.txt"
PR_BODY = OUT_DIR / "pr-body.md"
DIAGNOSIS = OUT_DIR / "diagnosis.md"
COPILOT_LOG = OUT_DIR / "copilot-output.jsonl"
COPILOT_ERR = OUT_DIR / "copilot-stderr.log"
