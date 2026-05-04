# PR triage scripts

Scripts that implement the `/spotless`, `/update-branch`, `/fix`, and
`/review` slash commands wired up by `.github/workflows/pr-triage-comments.yml`.

## Security model

Slash commands run on PRs that the repository owner does not necessarily
control. The workflow splits into separate jobs so that no single job
holds a privileged token AND executes PR-controlled code.

| Job              | Entry point          | Tokens visible                                | PR-controlled code allowed |
| ---------------- | -------------------- | --------------------------------------------- | -------------------------- |
| authorize-command| `authorize.py`       | `GITHUB_TOKEN`                                | none (default-branch checkout only) |
| pr-snapshot      | inline               | `GITHUB_TOKEN`                                | none — `gh pr checkout` + `git bundle`; PR tree never executed |
| gradle-worker    | `worker_gradle.py`   | `GITHUB_TOKEN`                                | yes — runs `./gradlew` on PR tree |
| copilot-worker   | `worker_copilot.py`  | `GITHUB_TOKEN`, `COPILOT_GITHUB_TOKEN`        | only Copilot CLI editing files; never `./gradlew` or other build tools |
| poster           | `poster.py`          | otelbot installation token                    | none — `git`/`gh` on the worker artifact only |

The PR working tree is checked out exactly once, in `pr-snapshot`, which
holds no privileged secrets. It is exported as a git bundle and consumed
by the worker jobs via `git fetch <bundle-file>`. This keeps `gh pr
checkout` (which CodeQL flags as untrusted) out of any job that holds
the Copilot token.

Invariants (see the comments at the top of each entry-point file):

* `gradle-worker` must never receive `COPILOT_GITHUB_TOKEN` or any other
  privileged token. Anything in the environment of `./gradlew` is reachable
  from a malicious PR build script.
* `copilot-worker` must never invoke `./gradlew` or any other PR-controlled
  build tooling. PR build files would otherwise see `COPILOT_GITHUB_TOKEN`.
* `poster` runs only trusted code snapshotted from the default branch
  (`$RUNNER_TEMP/pr-triage-trusted`). It never executes anything from the
  PR working tree, so `gh pr checkout` is safe even though the otelbot
  token is in scope.
* `/fix` hands off between the two workers via a CI bundle written to
  `out_dir/ci-bundle/` plus a `needs-copilot.txt` marker. The
  copilot-worker downloads that artifact and runs Copilot on the bundle;
  it never re-runs Gradle.

## Files

* `authorize.py` — entry point for the `authorize-command` job.
* `worker_gradle.py` — entry point for the `gradle-worker` job.
* `worker_copilot.py` — entry point for the `copilot-worker` job.
* `poster.py` — entry point for the `poster` job.
* `triage_helpers.py` — shared helpers for parsing the issue_comment
  event, posting comments, and running sub-scripts. No tooling that
  would weaken the role split lives here.
* `common.py` — shared helpers for the per-command sub-scripts
  (`spotless.py`, `update_branch.py`, `fix.py`, `review.py`).
* `spotless.py`, `update_branch.py`, `fix.py`, `review.py` — the
  per-command implementations invoked by `worker_gradle.py` /
  `worker_copilot.py`.
