---
mode: agent
---

# Fix PR CI failures

Analyze the CI failures in the PR for the current branch and fix them, following the structured plan below.

Each time this prompt is triggered, assume you are starting fresh from the beginning of the process.

Do not stop a given execution until you have worked through all phases below.

## Phase 0: Validate

1. Verify we're not on a protected branch: `git branch --show-current` should not be `main`
2. Check that the branch is up-to-date with remote: `git fetch && git status` - exit if `git status` reports the branch is `behind` or has `diverged` from remote.
3. Get the current branch name using `git branch --show-current`
4. Find the PR for this branch using `gh pr list --head <branch-name> --json number,title` and extract the PR number
5. Use `gh pr view <pr-number> --json statusCheckRollup --jq '.statusCheckRollup[] | select(.conclusion == "FAILURE") | {name: .name, detailsUrl: .detailsUrl, databaseId: .databaseId}'` to get the list of all failed CI jobs
6. Check if there are actually CI failures to fix - if all jobs passed, exit early

## Phase 1: Gather Information

**Phase 1 is for gathering raw log data and identifying failed tasks.**
**Allowed in Phase 1: downloading logs, grepping logs for error context, listing failed tasks. You may also read source files when strictly needed to classify a failure as real vs. flaky/infra.**
**Not allowed in Phase 1: editing code, or designing / planning fixes. Defer fix design to Phase 3.**

### Working directory discipline

- Always `cd` back to the repository root before running any `git`, `gh pr`, or `./gradlew` command. Log downloads in `/tmp` put the shell outside the repo and subsequent `git` commands will fail with `fatal: not a git repository`.
- Prefer one-shot invocations: `(cd /tmp && <download command>)` in a subshell, so the outer shell's cwd is preserved.

### Prevent CI-PLAN.md from being committed

Before creating `CI-PLAN.md`, add it to the repo-local ignore list so it can't be accidentally staged:

```
grep -qxF CI-PLAN.md .git/info/exclude || echo CI-PLAN.md >> .git/info/exclude
rm -f CI-PLAN.md
```

### Collect failure logs

1. Get repository info: `gh repo view --json owner,name`
2. Identify unique failing jobs:
   - **Ignore pure duplicates** that only differ by matrix parameters inside parentheses (e.g., `common / test0 (8, hotspot, indy false)` vs `(11, hotspot, indy false)`).
   - **Do sample axes that plausibly change behavior**: different JDK majors, indy true vs false, any `-deny-unsafe` / security-manager variants, and latest-deps vs pinned. Grab one representative per meaningfully distinct axis, not just one per job name prefix.
   - **Flaky / infra failures**: if a failure looks like a network timeout, cache miss, runner OOM, or anything clearly unrelated to PR code, note it in `CI-PLAN.md` under "Notes" and do not invent a code fix for it.
3. Download logs for the selected representatives. Two equally-valid options:

   **Option A: `gh run view`** (avoids putting the token in process argv):

   ```
   # For a single job's failing log only:
   gh run view --job <job-id> --log-failed > /tmp/<job-name>.log

   # Or all failed steps across the whole run in one file:
   gh run view <run-id> --log-failed > /tmp/run-<run-id>-failed.log
   ```

   **Option B: REST API** — use this on Windows/Git Bash, where `gh run view --log-failed` often fails with `stream error: stream ID 1; CANCEL; received from peer`:

   ```
   (cd /tmp && curl -sSfL \
     -H "Authorization: token $(gh auth token)" \
     -o <job-name>.log \
     "https://api.github.com/repos/<owner>/<repo>/actions/jobs/<job-id>/logs")
   ```

   Token safety:
   - Do NOT pipe `gh auth token` through `xargs` — that places the token in `argv` where other local processes can see it.
   - Inlining it via `-H "Authorization: token $(gh auth token)"` also exposes it in the `curl` process's `argv` (visible to `ps` on multi-user systems). Acceptable on a single-user dev box; avoid on shared machines.

4. For each downloaded log, extract failed Gradle tasks and error context:
   - Failed tasks: `grep "Task.*FAILED" /tmp/<job-name>.log`
   - Test failures: `grep "FAILED" /tmp/<job-name>.log | grep -E "(Test|test)"`
   - Compilation error context: `grep -B 5 -A 20 "error:" /tmp/<job-name>.log`
   - Task failure context: `grep -B 2 -A 15 "Task.*FAILED" /tmp/<job-name>.log`

### Fan-out shortcut

Large PRs often produce dozens of failures that all stem from a single upstream
task (e.g., one `compileJava` failure cascades into every test matrix cell).
After downloading the first 2-3 representatives, if they all report the
**same failed upstream Gradle task with identical error text**, stop sampling —
one fix will resolve all axes. Record the full list of failing jobs in
`CI-PLAN.md` but don't waste time downloading each one's log.

### Searching the repo

Use `rg` (ripgrep) for repo-wide text searches. It respects `.gitignore` by
default, so `build/`, `.gradle/`, and similar generated directories are skipped
automatically. Plain `grep -r` / `grep -rn` will descend into those
directories and may hang on large builds. `git grep` is also acceptable.

## Phase 2: Create CI-PLAN.md

**ONLY:** Create the CI-PLAN.md file in the repository root with the following structure:

```markdown
# CI Failure Analysis Plan

## Failed Jobs Summary
- Job 1: <job-name> (job ID: <id>)
- Job 2: <job-name> (job ID: <id>)
...

## Unique Failed Gradle Tasks

- [ ] Task: <gradle-task-path>
  - Seen in: <job-name-1>, <job-name-2>, ...
  - Log files: /tmp/<file1>.log, /tmp/<file2>.log

- [ ] Task: <gradle-task-path>
  - Seen in: <job-name-1>, <job-name-2>, ...
  - Log files: /tmp/<file1>.log, /tmp/<file2>.log

## Suspected Flaky / Infra Failures (skipped)
- <job-name>: <reason>

## Notes
[Any patterns or observations about the failures]
```

## Phase 3: Fix Issues

**Important**: Do not commit `CI-PLAN.md` — it's only for tracking work during the session. Phase 1 already added it to `.git/info/exclude`; do not use `git add -A` or `git commit -a` (either could still pick it up if the ignore entry was missed).

### Gradle execution rules (repo-specific)

- Never pipe Gradle output through `grep`, `tail`, `head`, or any other command. Piping masks Gradle's exit code, so a failing build silently appears to succeed.
- Run Gradle with no timeout (`timeout 0`) and wait for completion — builds and tests can take several minutes.
- Never use `--rerun-tasks`. Use `--rerun` when a task must be forced to re-execute.

### Per-task workflow

Work through `CI-PLAN.md`, checking items off as you complete them. For each failed task:

- Analyze the failure using the logs (now you may open source files).
- Implement the fix.
  - Spotless failures: `./gradlew :<failed-module-path>:spotlessApply`
- **Test locally before committing**:
  - Markdown lint: `mise run lint:markdown`
  - Compilation errors: `./gradlew <failed-task-path>`
  - Test failures: run the whole module's test task (e.g., `./gradlew :instrumentation:module-name:javaagent:test`) rather than just the single failing test — related tests in the module often need fixing too.
  - Verify the fix resolves the issue.
- Update the checkbox in `CI-PLAN.md`.
- Commit each logical fix as a **separate commit**:
  - Use explicit pathspecs: `git commit -- path/one path/two -m "..."`.
  - Note that `git mv` auto-stages the rename — don't re-run `git add` on the moved paths.
  - If `git mv` or earlier edits have already staged unrelated changes, `git reset` first and stage only the files for the current logical fix.
  - Do not commit `CI-PLAN.md`.
- Do not `git push` in this phase.

## Phase 4: Validate and Push

- Delete `CI-PLAN.md` (`rm -f CI-PLAN.md`) and confirm `git status` is clean of it.
- Push the changes with plain `git push`.
  - Only use `git push -f` / `--force-with-lease` if you rewrote history during the session (rebase, amend, reorder). Additive fix commits do not require a force push.
- Provide a summary of:
  - What failures were found (including any skipped as flaky/infra).
  - What fixes were applied.
  - Which commits were created.
