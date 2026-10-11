# AGENTS.md

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. It is the source of truth for repository layout,
build and test commands, style expectations, and scope.

## Pull request titles and descriptions

For PR creation and updates, follow `CONTRIBUTING.md`, required title conventions, and the PR template.

- Before finishing, compare the title and body with the complete final PR diff. Fix misleading text,
  but leave accurate metadata alone. Preserve confirmed user intent; never change sound code to fit
  stale metadata or rewrite descriptions to excuse bugs.
- Use a short, specific, nonempty, one-line title. The body may be empty; otherwise open with one or
  two short sentences saying what changes and why, without a generic heading.
- Keep user-visible behavior, rationale, compatibility/migration notes, risks, and non-obvious
  constraints. Remove filler, repetition, obvious implementation details, and change-log-style updates.
- For changed public/programmatic APIs or user/operator configuration, put a small useful example near
  the top. Show old/new forms only for migration; cover relevant forms such as flat properties and YAML
  without redundant spellings. Internal CI/build/maintenance settings are not user-facing interfaces.
- Use short paragraphs, concise bullets, and meaningful headings only for substantial topics. Preserve
  useful context and examples when compressing drafts. Never hard wrap GitHub text.
- Omit `Summary`, `Details`, and `Testing` headings, validation lists, and checklist boilerplate unless
  explicitly requested or required by the template.

These rules do not authorize comments, thread resolution, or changing an existing PR's draft status.

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
