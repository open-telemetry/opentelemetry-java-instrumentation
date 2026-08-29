# Flaky-test fix toolkit

A small pipeline that picks the most-flaky JUnit test from Develocity and asks
the Copilot CLI agent to fix it. It is run by the
[`flaky-test-remediation.yml`](../../workflows/flaky-test-remediation.yml) workflow.

## Pipeline

```text
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

All intermediate files live under `build/flaky-test-remediation/` (gitignored). Paths are
centralized in [`_paths.py`](_paths.py).

## Files

| File                     | Role                                                                                                                                                                                  |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `1-select-flaky-test.py` | Hits Develocity dashboard endpoints, picks one flaky test, writes `selected.json`.                                                                                                    |
| `2-fix-flaky-test.py`    | Renders `prompt.txt` and invokes the Copilot CLI agent to fix the test (writes `copilot-output.jsonl`, `copilot-stderr.log`, and — if Copilot follows the protocol — `diagnosis.md`). |
| `3-open-pr.py`           | Renders `pr-body.md` with the failure's Gradle Build Scan links (and `diagnosis.md` if Copilot wrote one), then opens the PR via `gh pr create`.                                      |
| `_paths.py`              | Single source of truth for every file under `build/flaky-test-remediation/`.                                                                                                          |
| `_render.py`             | Tiny formatting helpers shared by the renderers.                                                                                                                                      |

## Skip list / progress tracking

The orphan branch `otelbot/flaky-test-remediation-progress` carries an
`attempted.txt` file with one Develocity test container/class name per line.
The workflow reads it as a skip list so it doesn't keep retrying related flaky
methods in the same class. It appends the selected class after each successful
attempt.

## Workflow usage

Run `Flaky Test Remediation` manually from GitHub Actions. Each run analyzes
one test class, uploads the analysis and fix diagnostics as workflow artifacts,
and opens an automated draft PR when Copilot produces a change.

## Environment

`DEVELOCITY_URL` (defaults to `https://develocity.opentelemetry.io`). The
dashboard data endpoints used here are unauthenticated; no access key needed.
