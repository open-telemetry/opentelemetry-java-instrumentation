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

## Database and RPC status codes

- Keep status availability separate from failure classification. Capture available success,
  warning, expected-negative, and failure codes. Do not use success flags, an `ERR_` prefix,
  HTTP-like ranges, positivity, or server provenance as universal availability or failure
  tests. Adding status capture must preserve existing error classification, exception
  fallback, span status, exception events, and metric grouping unless intentionally changed.
  A code can also be an error type, but the final `error.type` is not a status-code source.
- Follow the applicable system-specific representation and supported client API's availability
  rules. Do not impose numeric-only, enum-only, or literal-wire-value rules; normalized or
  synthetic client statuses are not inherently invalid. Zero and negative codes are not
  inherently absent. Retain type, parsing, and range validation. Use bounded codes, not
  arbitrary messages, payloads, exception class names, or `toString()` output, and do not invent
  success codes when the observed API exposes none.
- Capture retained codes at completion even when result retrieval throws, times out, or is
  cancelled. When status and error classification use different eligibility rules, select
  independently and apply eligibility during selection so an ineligible first value cannot
  hide a later eligible value.
- For SQLSTATE and vendor-code selection, prefer the more specific code when established,
  fall back to the other available code when the preferred code is absent, and otherwise
  retain both as `SQLSTATE/vendorCode`. Use verified driver semantics and canonical database
  identities, not SQL-dialect assumptions.
- Reuse identical extraction and remove pass-through wrappers, but retain adapters with
  different classification policies. Compare native numeric values before string conversion.
  Name codes shared by status and classification `errorCode` or `expectedErrorCode`; use
  `statusCode` for status-only values and `errorType` for classification-only values. Keep
  separate expectations when values can differ. Leave semantic API getter names and
  telemetry attribute names unchanged.

## Schema URLs

Select the schema URL from the semantic convention defining the emitted span, metric, or log.
Its version follows the convention adopted by that signal and its effective mode, not the latest
semconv dependency. Registered attributes alone do not establish a signal
convention; supporting attributes must not select, change, or invalidate its schema.
If no semantic convention defines the emitted signal, leave its schema URL unset.
