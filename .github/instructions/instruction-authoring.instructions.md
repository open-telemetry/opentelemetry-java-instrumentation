---
applyTo: ".github/copilot-instructions.md,.github/instructions/**/*.instructions.md"
---

# Writing repository instructions

- State what must be true of the code, tests, configuration, or documentation. That includes
  compatibility guarantees across releases. Most rules don't need steps. Include a short step in
  the same bullet only if it gives the reader a non-obvious way to meet or check the rule.
- Use sections for guidance that needs more space or applies to several rules. For example,
  `Validation` can group checks with several steps.
- Each requirement that can be violated and fixed independently has its own top-level `-` bullet.
- Keep a rule's conditions, exceptions, rationale, and examples in the same bullet so it makes
  sense on its own. Indent continuation paragraphs, nested lists, tables, and code fences by two
  spaces.
- Use headings to group related rules. State each rule's conditions in the bullet itself.
- If a file needs a scope note, put it in one plain paragraph immediately below the `#` title,
  without a separate heading. Use the note to say where the file's rules apply, not to state
  individual requirements.
