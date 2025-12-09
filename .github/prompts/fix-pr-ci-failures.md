---
mode: agent
---

# Fix PR CI failures

Analyze the CI failures in the PR for the current branch and fix them, following the structured plan below.

Each time this prompt is triggered, assume you are starting fresh from the beginning of the process.

Do not stop a given execution until you have worked through all phases below.

## Phase 0: Validate

1. Verify we're not on a protected branch: `git branch --show-current` should not be `main`
2. Check that the branch is up-to-date with remote: `git fetch && git status` - exit and warn if behind
3. Get the current branch name using `git branch --show-current`
4. Find the PR for this branch using `gh pr list --head <branch-name> --json number,title` and extract the PR number
5. Use `gh pr view <pr-number> --json statusCheckRollup --jq '.statusCheckRollup[] | select(.conclusion == "FAILURE") | {name: .name, detailsUrl: .detailsUrl}'` to get the list of all failed CI jobs
6. Check if there are actually CI failures to fix - if all jobs passed, exit early

## Phase 1: Gather Information

**Phase 1 is for gathering information ONLY. Do NOT analyze failures or look at any code during this phase.**
**Your only goal in Phase 1 is to collect: job names, job IDs, log files, and failed task names.**

1. Get repository info: `gh repo view --json owner,name`
2. For unique job type that failed
   - **Important**: Ignore duplicate jobs that only differ by parameters inside of parenthesis. For example:
     - In `abc / def (x, y, z)`, the job is `abc / def` while x, y, and z are parameters
   - **Strategy**: Download logs for 1-2 representative jobs first to identify patterns (e.g., one "build" job, one "test0" job)
   - Compilation failures typically repeat across all jobs, so you don't need every log file
   - Retrieve logs for selected representative jobs:
     - Get the job ID from the failed run by examining the PR status checks JSON
     - Download logs using: `cd /tmp && gh auth token | xargs -I {} curl -sSfL -H "Authorization: token {}" -o <job-name>.log "https://api.github.com/repos/<owner>/<repo>/actions/jobs/<job-id>/logs"`
     - Example: `cd /tmp && gh auth token | xargs -I {} curl -sSfL -H "Authorization: token {}" -o test0-java8-indy-false.log "https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/actions/jobs/53713949850/logs"`
     - The GitHub API responds with an HTTP 302 redirect to blob storage; `-L` (already included) ensures the download follows the redirect and saves the final log contents.
     - Find all gradle tasks that failed:
       - Search for failed tasks: `grep "Task.*FAILED" /tmp/<job-name>.log`
       - Look for test failures: `grep "FAILED" /tmp/<job-name>.log | grep -E "(Test|test)"`
       - Example output: `> Task :instrumentation:cassandra:cassandra-4.0:javaagent:test FAILED`
     - Extract error context:
       - For compilation errors: `grep -B 5 -A 20 "error:" /tmp/<job-name>.log`
       - For task failures: `grep -B 2 -A 15 "Task.*FAILED" /tmp/<job-name>.log`

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

## Notes
[Any patterns or observations about the failures]
```

## Phase 3: Fix Issues

**Important**: Do not commit CI-PLAN.md - it's only for tracking work during the session

- Work through the CI-PLAN.md, checking items off as you complete them
- For each failed task:
  - Analyze the failure
  - Implement the fix
    - For spotless failures: `./gradlew spotlessApply` to auto-fix formatting issues
  - **Test loc
  ally before committing**:
    - For compilation errors: `./gradlew <failed-task-path>`
    - For test failures: `./gradlew <failed-test-task>`
    - Verify the fix resolves the issue
  - Update the checkbox in CI-PLAN.md
  - Commit each logical fix as a separate commit
    - Reminder: do not commit CI-PLAN.md
- Do not git push in this phase

## Phase 4: Validate and Push

- Once all fixes are committed, push the changes: `git push` (or `git push -f` if needed)
- Provide a summary of:
  - What failures were found
  - What fixes were applied
  - Which commits were created
