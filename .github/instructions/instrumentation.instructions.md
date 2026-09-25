---
applyTo: "instrumentation/**/*,instrumentation-api/**/*,instrumentation-api-incubator/**/*"
---

# Instrumentation contracts

Apply a check only if the changed code affects that contract. Inspect unchanged neighboring
files when necessary, but comment on a changed line and only for a supported issue.

## Package naming

- New or renamed library packages use `io.opentelemetry.instrumentation.<library>...`; javaagent
  packages use `io.opentelemetry.javaagent.instrumentation.<library>...`.
- Encode version segments as one identifier such as `v4_0`, not dotted package segments such as
  `v4.0`. Put `common` after the version only for a version-scoped common module
  (`<library>.v4_0.common`); a non-versioned common module uses the ordinary library package
  without a trailing `common` segment.

## Emitted telemetry

- For a change implementing semantic conventions, establish the applicable released convention
  and effective mode from the module's telemetry contract. The semconv dependency version is
  not proof that the instrumentation adopted its latest rules. If the target cannot be
  established, do not report a version-specific violation.
- Compare the whole applicable convention for the signal, role, and operation: emission
  boundary, names, kinds, status, metric units, attributes and value rules, omissions, error
  classification, privacy, and cardinality. Check specific domain rules and footnotes before
  general guidance. A key's presence in the attribute registry alone does not establish
  applicability.
- Evaluate inclusion level separately from value rules. Report a feasible, unexplained
  departure from `SHOULD` or `RECOMMENDED` as a departure from guidance, not a `MUST`
  violation. Accept justified constraints and permitted `MAY` choices. Reuse a semconv
  constant only when both key name and type match; in library production code copy
  incubating constants locally rather than importing them. Tests may import both
  stable and incubating constants directly.
