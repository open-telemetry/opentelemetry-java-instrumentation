---
applyTo: ".github/copilot-instructions.md,.github/instructions/**/*.instructions.md"
---

# Writing repository instructions

- State what must be true of the code, tests, configuration, or documentation. That includes
  compatibility guarantees across releases. Keep short steps for meeting or checking a rule in
  the same bullet. A checkout audit can report a violation of the rule, but cannot infer that
  someone skipped a step.
- Use sections for guidance that needs more space or applies to several rules. For example,
  `Validation` can group checks with several steps, and review and audit sections can explain
  how to report findings.
- Each requirement that can be violated and fixed independently has its own top-level `-` bullet.
- A rule's conditions, exceptions, rationale, and examples stay inside its bullet. Continuation
  paragraphs, nested lists, tables, and code fences are indented two spaces under `-`. This keeps
  the complete rule together for the
  [instruction audit parser](https://github.com/open-telemetry/shared-workflows/blob/9b20b725a2dd19e03d3f9605c51680405373d217/.github/scripts/instruction-audit/rules.py).
- Headings label topics. Each rule states the conditions it depends on instead of relying on its
  heading. File-wide scope notes and standalone reference tables can stay outside bullets.
