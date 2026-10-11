# AGENTS.md

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. It is the source of truth for repository layout,
build and test commands, style expectations, and scope.

## Pull request titles and descriptions

Apply these rules when creating a pull request or updating one after implementation changes.
Follow `CONTRIBUTING.md`, any required title conventions, and the pull request template.

- Base the title and body on the complete actual pull request diff. Before finishing implementation
  or updating an existing pull request, compare both with the final diff and correct inaccurate or
  misleading text. Keep accurate metadata; do not churn it for small implementation details or turn
  the body into a change log.
- Keep the title nonempty, on one line, short, and specific to the final change.
- Preserve confirmed user intent and useful context in supplied or existing text. Metadata can be
  stale or mistaken: do not change sound code to satisfy an inaccurate description, or rewrite the
  description to excuse a bug.
- The body may be empty. Otherwise, open with one or two short sentences explaining what changes
  and why it matters, without a generic heading. The whole body is the summary: omit `Summary`,
  `Details`, and `Testing` headings, test-command or validation lists, and checklist boilerplate
  unless the user explicitly requests them or the repository template requires them.
- When a public or programmatic API or user/operator configuration changes, put the smallest useful
  concrete example near the top. Show old and new forms only when callers must migrate. Cover
  relevant configuration forms, such as flat properties and YAML, without repeating equivalent
  spellings. Repository-internal CI, build, and maintenance configuration is not a user-facing
  interface.
- Keep user-visible behavior, rationale, compatibility or migration notes, risks, and design
  constraints that the diff cannot explain. Omit implementation details obvious from the diff and
  validation details by default.
- Optimize for a quick scan with short paragraphs and concise bullets for parallel facts. Use
  meaningful topic headings only for substantial topics, not for every paragraph. Never hard wrap
  GitHub text.
- Compress the first draft to remove repetition, filler, and details visible in the diff while
  preserving meaning and useful examples.

These are content guidelines, not permission to post comments, resolve review threads, or change
an existing pull request's draft status.

## Knowledge Loading

For any task that analyzes or changes repository code, consult
`.github/agents/knowledge/README.md` before drawing conclusions. Load only the
articles relevant to the task.

Before introducing `WeakReference`, `WeakHashMap`, `Cache.weak()`, or another
identity-keyed registry to attach javaagent state to a third-party object, load
`.github/agents/knowledge/javaagent-virtual-fields.md`.

## Gradle Execution Rules

- Never use `--rerun-tasks`. Use `--rerun` when needed.
- Builds and tests can take several minutes. Run Gradle with timeout `0` and wait.
  Slow output is not a hang.
- Never pipe Gradle output through `tail`, `head`, `grep`, etc. Piping masks the
  Gradle exit code.
