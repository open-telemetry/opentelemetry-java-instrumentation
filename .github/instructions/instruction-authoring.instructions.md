---
applyTo: ".github/copilot-instructions.md,.github/instructions/**/*.instructions.md"
---

# Writing repository instructions

- Repository rules give coding agents and pull request reviewers a standard for acceptable
  implementations, tests, configuration, and documentation. Compatibility guarantees across
  releases are also rules. Procedures for editing or validating a change, such as when to run a
  Gradle task, belong in the relevant workflow documentation. Review and audit reporting guidance
  stays separate from repository rules.
- Each requirement that can be violated and fixed independently has its own top-level `-` bullet.
- A rule's conditions, exceptions, rationale, and examples stay inside its bullet. Continuation
  paragraphs, nested lists, tables, and code fences are indented two spaces under `-`. This keeps
  the complete rule together for the
  [instruction audit parser](https://github.com/open-telemetry/shared-workflows/blob/9b20b725a2dd19e03d3f9605c51680405373d217/.github/scripts/instruction-audit/rules.py).
- Headings label topics. Each rule states the conditions it depends on instead of relying on its
  heading. File-wide scope notes and standalone reference tables can stay outside bullets.
