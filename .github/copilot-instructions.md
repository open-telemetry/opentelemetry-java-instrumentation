# OpenTelemetry Java Instrumentation

## Scope

Repository-wide defaults for Copilot behavior.
Keep scoped language or file-type rules in `.github/instructions/*.instructions.md`.

## Repository Layout

- Workspace-level overview: `.github/README.md`
- Scoped instructions and `applyTo` patterns: `.github/instructions/README.md`
- Agent review and implementation knowledge index: `.github/agents/knowledge/README.md`

## Core References

- Style guide and core conventions: `docs/contributing/style-guide.md`
- Review and implementation knowledge by topic: `.github/agents/knowledge/README.md`

## Gradle Execution Rules

Never use `--rerun-tasks`. Use `--rerun` when needed.

Builds and tests in this repository can take several minutes.
Run Gradle commands with timeout `0` (no timeout), and wait for completion.
Do not treat slow output as a hang by default.
