---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Emitted telemetry

Apply these checks when changed production code emits or modifies telemetry,
including instrumentation, SDK support, agent support, and extension modules.
Comment only on a changed line and only for a supported issue.

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
