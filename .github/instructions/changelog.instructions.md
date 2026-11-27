---
applyTo: "CHANGELOG.md"
---

# Changelog entries

- Outside release preparation, add a hand-written entry in `Unreleased` for deprecations and
  breaking changes only, as specified in [CONTRIBUTING.md](../../CONTRIBUTING.md). This includes
  deprecations of settings and instrumentation enablement aliases. Leave other changes to the
  generated changelog.
- Put breaking changes under the appropriate stable or non-stable heading in `Unreleased`.
- Keep entries short. For deprecations, name the deprecated interface and its replacement without
  repeating removal timing or obvious migration steps.
- Describe the behavior users get in the release. Omit preview flags used to stage the change
  unless users need to know that the release changes or removes a flag's effect.
- Omit unchanged behavior.
- If a migration applies only to a particular API, configuration form, or deployment mode, say
  which one.
- For a breaking flat property rename, where the old name is no longer supported, use
  `Rename <old flat property> to <new flat property>.` When the old name remains supported as a
  deprecated alias, use `Deprecate <old flat property> in favor of <new flat property>.`
- Omit YAML key renames that only repeat a flat property rename. Include YAML details when they
  describe different behavior, a distinct migration, or configuration with no flat equivalent.
