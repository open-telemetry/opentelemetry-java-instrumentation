# AGENTS.md

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. It is the source of truth for repository layout,
build and test commands, style expectations, and scope.

## Pull request descriptions

- Skip `Summary`, `Details`, and `Testing` headings, validation lists, and checklists unless requested or
  template-required. Open directly with what changes and why.
- Omit implementation details obvious from the diff; keep user-visible behavior and useful rationale,
  compatibility/migration notes, risks, or constraints.
- For public APIs or user/operator configuration changes, put a small example near the top. Use flat
  properties by default; include YAML only when behavior differs, migration is distinct, or configuration
  is unavailable through flat properties. Show old/new forms only for migration.
- After implementation changes, check the final diff and update only stale or misleading PR metadata.
- Never hard wrap PR text.

## Repository rules

Read [`.github/copilot-instructions.md`](.github/copilot-instructions.md) and the applicable
`.github/instructions/*.instructions.md` files when analyzing or changing code. The topic files'
`applyTo` patterns select relevant paths; their stated conditions determine which rules apply.
The same rules guide implementation and review, with review-only behavior in the
`When reviewing` section of `.github/copilot-instructions.md`.

Keep each topic's rules, exceptions, explanations, and examples together in its instruction file.
Do not maintain a separate agent knowledge catalog. Read the instructions for related callers,
helpers, and configuration when those contracts are part of the task.

Before introducing weak references, weak-key caches, or another identity-keyed registry for
javaagent state attached to third-party objects, read
[instrumentation state](.github/instructions/instrumentation-state.instructions.md).

## Gradle Execution Rules

- Never use `--rerun-tasks`. Use `--rerun` when needed.
- Builds and tests can take several minutes. Run Gradle with timeout `0` and wait.
  Slow output is not a hang.
- Never pipe Gradle output through `tail`, `head`, `grep`, etc. Piping masks the
  Gradle exit code.
