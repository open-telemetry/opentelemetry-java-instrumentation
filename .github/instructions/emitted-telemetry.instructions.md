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

- Capture available codes for any outcome. Follow protocol/client semantics, not universal
  tests based on success flags, `ERR_` prefixes, HTTP-like ranges, sign, or server provenance.
  Symbolic, normalized, synthetic, zero, and negative codes can be valid.
- Validate code types/ranges and use bounded codes, not arbitrary messages, payloads,
  exception classes, or `toString()` output. Do not invent missing codes.
- Keep status-only changes from altering error classification, exception fallback, span
  status/events, or metric grouping. A code can be an error type; do not derive codes from
  the final `error.type`. Capture retained codes on failed, timed-out, or cancelled completion.
  Select independently when eligibility differs, skipping ineligible candidates.
- For SQLSTATE/vendor codes, prefer the more specific code when known. Fall back to the
  other available code; without a known preference, retain both as `SQLSTATE/vendorCode`.
  Use canonical database identities, not SQL-dialect assumptions.
- Share identical extraction, retain different classification policies, and compare numeric
  values before string conversion. Use `errorCode`/`expectedErrorCode` for shared codes,
  `statusCode` for status-only values, and `errorType` for classification-only values. Keep
  separate expectations when values can differ. Leave semantic API getters and attribute
  names unchanged.

## Schema URLs

Select the schema URL from the semantic convention defining the emitted span, metric, or log.
Its version follows the convention adopted by that signal and its effective mode, not the latest
semconv dependency. Registered attributes alone do not establish a signal
convention; supporting attributes must not select, change, or invalidate its schema.
If no semantic convention defines the emitted signal, leave its schema URL unset.
