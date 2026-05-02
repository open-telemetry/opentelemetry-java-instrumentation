---
mode: agent
---

# Fix PR CI failures

Analyze the CI failures in the PR for the current branch and fix them, following the structured plan below.

## Phase 0: Validate

1. Verify we're not on a protected branch: `git branch --show-current` should not be `main`.
2. Check the branch is up-to-date: `git fetch && git status` — exit if `behind` or `diverged`.
3. Find the PR and its failed jobs:

   ```
   BRANCH=$(git branch --show-current)
   PR=$(gh pr list --head "$BRANCH" --json number --jq '.[0].number')
   gh pr view "$PR" --json statusCheckRollup \
     --jq '.statusCheckRollup[] | select(.conclusion == "FAILURE") | {name, detailsUrl, databaseId}'
   ```

4. **Ignore aggregate/rollup checks** like `required-status-check` — fixing the real underlying checks resolves them automatically.
5. If all remaining jobs passed, exit early.
6. **Trivial-failure fast path**: if there is exactly one failing job with an obvious single root cause (e.g., one markdownlint rule, one spotless violation, one known-flaky infra error), skip Phases 1–2 — fix directly, validate, commit.

## Phase 1: Gather Information

**Phase 1 is only for gathering raw log data and identifying failed tasks.** You may read source files when needed to classify a failure as real vs. flaky/infra. Do not edit code or design fixes yet.

### Identify failing jobs to sample

- **Ignore pure duplicates** that only differ by matrix parameters inside parentheses (e.g., `common / test0 (8, hotspot, indy false)` vs `(11, hotspot, indy false)`).
- **Do sample axes that plausibly change behavior**: different JDK majors, indy true vs false, `-deny-unsafe` / security-manager variants, latest-deps vs pinned. One representative per meaningfully distinct axis.
- **Flaky / infra failures**: network timeouts, cache misses, runner OOM, or anything clearly unrelated to PR code — note in `/tmp/ci-plan.md` under "Notes" and do not invent a code fix.

### Download logs

Use the REST API (more reliable than `gh run view --log-failed`, which on Windows/Git Bash sometimes fails or silently produces a 0-byte file):

```
(cd /tmp && curl -sSfL \
  -H "Authorization: token $(gh auth token)" \
  -o <job-name>.log \
  "https://api.github.com/repos/<owner>/<repo>/actions/jobs/<job-id>/logs")
```

Use subshells (`(cd /tmp && …)`) for `/tmp` operations so the outer shell stays in the repo root. Do not pipe `gh auth token` through `xargs` (puts the token in argv).

### Extract errors

```
grep -B2 -A20 -E "error:|Task.*FAILED" /tmp/<job-name>.log
```

### Fan-out shortcut

Large PRs often produce dozens of failures that all stem from a single upstream task (e.g., one `compileJava` failure cascades into every test matrix cell). After 2–3 representatives, if they all report the **same failed upstream Gradle task with identical error text**, stop sampling — one fix will resolve all axes. Record the full list in the plan but don't download each log.

## Phase 2: Create plan

Create `/tmp/ci-plan.md` (outside the repo — no risk of accidental commit):

```markdown
# CI Failure Analysis Plan

## Failed Jobs Summary

- Job 1: <job-name> (job ID: <id>)
...

## Unique Failed Gradle Tasks

- [ ] Task: <gradle-task-path>
  - Seen in: <job-name-1>, <job-name-2>, ...
  - Log files: /tmp/<file1>.log, ...

## Suspected Flaky / Infra Failures (skipped)

- <job-name>: <reason>

## Notes
[Patterns or observations]
```

## Phase 3: Fix Issues

Work through `/tmp/ci-plan.md`, checking items off. For each failed task:

- Analyze the failure using the logs (now you may open source files).
- Implement the fix.
  - Spotless failures: `./gradlew :<module-path>:spotlessApply`
  - Markdown lint failures: most rules have no auto-fix — edit manually. `mise run lint:markdown` only validates.
- **Test locally before committing**:
  - Markdown lint: `mise run lint:markdown`
  - Compilation errors: `./gradlew <failed-task-path>`
  - Test failures: run the whole module's test task rather than just the single failing test — related tests often need fixing too.
- Commit each logical fix as a **separate commit** with explicit pathspecs. **Put `-m` before `--`** (everything after `--` is a pathspec): `git commit -m "..." -- path/one path/two`.
  - `git mv` auto-stages the rename — don't re-run `git add` on moved paths.
  - If unrelated changes are already staged, `git reset` first.
- Do not `git push` in this phase.

## Phase 4: Push

`git push`, then summarize:

- What failures were found (including any skipped as flaky/infra).
- What fixes were applied.
- Which commits were created.
