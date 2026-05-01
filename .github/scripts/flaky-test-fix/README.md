# Flaky-test fix toolkit

A small pipeline that picks the most-flaky JUnit test from Develocity and asks
the Copilot CLI agent to fix it. Drives both the
[`flaky-test-fix.yml`](../../workflows/flaky-test-fix.yml) workflow and a
local equivalent.

## Pipeline

```
   skip.txt (from progress branch)
        |
        v
  +------------------+   selected.json   +-----------------+   prompt.txt + diagnosis.md
  | 1-select-flaky-  | ----------------> | 2-fix-flaky-    | -------------------+
  |   test           |                   |   test          |                    |
  | (Develocity REST)|                   | (renders prompt |                    |
  +------------------+                   |  + invokes CLI) |                    |
                                         +-----------------+                    v
                                                                       +------------------+
                                                                       | 3-open-pr        |
                                                                       | (renders pr-body |
                                                                       |  + gh pr create) |
                                                                       +------------------+
```

All intermediate files live under `build/flaky-fix/` (gitignored). Paths are
centralized in [`_paths.py`](_paths.py).

## Files

| File | Role |
| --- | --- |
| `1-select-flaky-test.py` | Hits Develocity dashboard endpoints, picks one flaky test, writes `selected.json`. |
| `2-fix-flaky-test.py` | Renders `prompt.txt` and invokes the Copilot CLI agent to fix the test (writes `copilot-output.jsonl`, `copilot-stderr.log`, and — if Copilot follows the protocol — `diagnosis.md`). |
| `3-open-pr.py` | Renders `pr-body.md` (incorporates `diagnosis.md` if Copilot wrote one) and opens the PR via `gh pr create`. |
| `_paths.py` | Single source of truth for every file under `build/flaky-fix/`. |
| `_render.py` | Tiny formatting helpers shared by the renderers. |
| `run-local.py` | Local driver: runs the same pipeline as CI, pushes to `origin`, opens the PR against `upstream/main`. |

## Skip list / progress tracking

The orphan branch `otelbot/flaky-test-fix-progress` carries a single
`attempted.txt` file (one fully-qualified test name per line). Both the CI
workflow and `run-local.py` read it as a skip list so we don't keep retrying
the same test, and append to it after each successful attempt.

## Local usage

```bash
python .github/scripts/flaky-test-fix/run-local.py            # opens a PR
python .github/scripts/flaky-test-fix/run-local.py --draft    # opens a draft
```

Requires:
- `git` remotes `origin` (your fork) and `upstream`
  (`open-telemetry/opentelemetry-java-instrumentation`)
- `gh` authenticated for the upstream repo
- `copilot` CLI on PATH

## Environment

`DEVELOCITY_URL` (defaults to `https://develocity.opentelemetry.io`). The
dashboard data endpoints used here are unauthenticated; no access key needed.
