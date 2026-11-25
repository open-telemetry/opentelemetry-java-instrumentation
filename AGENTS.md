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
`.github/instructions/*.instructions.md` files when analyzing, auditing, or changing code.
The topic files' `applyTo` patterns select relevant paths; their stated conditions determine which
rules apply. Apply the same code rules to a checkout audit and a pull request review, but follow
the reporting rules for each task in `.github/copilot-instructions.md`.

Keep each topic's rules, exceptions, explanations, and examples together in its instruction file.
Do not maintain a separate agent knowledge catalog. Read the instructions for related callers,
helpers, and configuration when those contracts are part of the task.

Before introducing weak references, weak-key caches, or another identity-keyed registry for
javaagent state attached to third-party objects, read
[instrumentation state](.github/instructions/instrumentation-state.instructions.md).

### Writing repository instructions

Write these instructions for coding agents and pull request reviewers. The instruction audit uses
the same rules.

- Use one top-level `-` bullet per requirement that can be applied on its own. If two requirements
  can fail independently and need different fixes, give them sibling bullets.
- Keep a rule's conditions, exceptions, rationale, and examples inside its bullet. Indent
  continuation paragraphs, nested lists, tables, and code fences two spaces under `-`.
- Use headings for navigation, not to supply a rule's conditions. File-wide scope notes and
  standalone reference tables can stay outside bullets.
- When restructuring rules, check the blocks with the parser version used by the audit. See the
  [instruction audit parser](https://github.com/open-telemetry/shared-workflows/blob/9b20b725a2dd19e03d3f9605c51680405373d217/.github/scripts/instruction-audit/rules.py)
  for how Markdown maps to blocks. Verify that related text stays together and unrelated rules
  remain separate.

## Gradle Execution Rules

- Never use `--rerun-tasks`. Use `--rerun` when needed.
- Builds and tests can take several minutes. Run Gradle with timeout `0` and wait.
  Slow output is not a hang.
- Never pipe Gradle output through `tail`, `head`, `grep`, etc. Piping masks the
  Gradle exit code.
