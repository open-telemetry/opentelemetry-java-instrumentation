---
applyTo: "instrumentation/**/*,instrumentation-api/**/*,instrumentation-api-incubator/**/*"
---

# Instrumentation contracts

Apply a check only if the changed code affects that contract. Inspect unchanged neighboring
files when necessary, but comment on a changed line and only for a supported issue.

## Configuration

- When adding or changing an `otel.instrumentation.*` setting, check both its flat property
  and declarative YAML name. Experimental or preview names are unstable; an experimental
  flat name must map to the corresponding `/development` YAML form. Do not rename an
  already-published declarative name merely to match mechanical conversion; determine whether
  the bridge needs a `SPECIAL_MAPPINGS` entry instead.
- Stable flat property names remain stable even when read by alpha implementation code.
  On rename, retain the old name until the next major version: read the replacement first,
  fall back to the old name only outside v3-preview, and warn once at startup *when the old
  value is applied*. Add the deprecation to the CHANGELOG. Experimental/preview names may
  be removed after a subsequent minor release and do not need the v3-preview guard.
  Instrumentation enablement aliases have distinct warning semantics; do not apply ordinary
  replacement-first warning logic to them.
- Ordinary instrumentation settings are read through `DeclarativeConfigUtil`, with a default
  for unavailable YAML. A nullable read is intentional when probing for a replacement or
  deprecated name before choosing a default. Flat `ConfigProperties` reads are reserved for
  enablement bootstrapping in `AgentDistributionConfig`. Structured YAML-only settings need
  declarative-mode coverage.
- For module code changes, check the associated `metadata.yaml` against actual reads, types,
  and defaults, including common-module dependencies. Do not add the module's general enabled
  setting to its configuration list. Check explicit metadata edits under the metadata-specific
  instructions.

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
