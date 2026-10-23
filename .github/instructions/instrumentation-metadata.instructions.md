---
applyTo: "instrumentation/**/metadata.yaml"
---

# Instrumentation metadata

- Compare each changed config entry with the property read by the module or its dependent
  common modules. Flag wrong names, types, defaults, unused settings, or missing settings only
  when the code establishes the mismatch; do not add the general module enabled/disabled
  property as a config entry.
- Check flat and declarative forms separately. `type` describes the flat form;
  `declarative_type` and `declarative_schema` describe a different declarative form when
  needed. Structured lists require an object schema with `required` keys drawn from its
  `properties`. A declarative-only entry may omit `name` but must have `declarative_name`.
- Experimental flat names must correspond to `/development` YAML names. Preserve an existing
  published YAML name when a mechanical rename would break users; add a bridge mapping only
  if normalized flat-property lookup does not resolve it.
- Add `examples` only for module-specific settings with non-obvious formats, never boolean,
  `general.*`, or `java.common.*` settings. Check that a referenced common setting's
  module-specific override immediately precedes its `ref`.
