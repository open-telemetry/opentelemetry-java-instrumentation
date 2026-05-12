# AGENTS.md

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. It is the source of truth for repository layout,
build and test commands, style expectations, and scope.

## Knowledge Loading

For coding, fix, and refactoring tasks, consult `.github/agents/knowledge/README.md`
and load only the article(s) relevant to the current task.

## Gradle Execution Rules

- Never use `--rerun-tasks`. Use `--rerun` when needed.
- Builds and tests can take several minutes. Run Gradle with timeout `0` and wait.
  Slow output is not a hang.
- Never pipe Gradle output through `tail`, `head`, `grep`, etc. Piping masks the
  Gradle exit code.
