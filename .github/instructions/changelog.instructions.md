---
applyTo: "CHANGELOG.md"
---

# Changelog entries

- Put breaking changes under the appropriate stable or non-stable heading in `Unreleased`.
- Keep entries short. For deprecations, name the deprecated interface and its replacement without
  repeating removal timing or obvious migration steps.
- Describe the released behavior. Omit preview flags used to stage the change and mention
  unchanged behavior only when it clarifies the change's scope.
- For a flat property rename, use `Rename <old flat property> to <new flat property>.` Omit
  one-to-one YAML key renames that mirror flat property renames. Follow the
  [general documentation rule](../copilot-instructions.md#documentation) for when to include
  declarative configuration.
