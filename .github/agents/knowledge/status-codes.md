# [Semconv] Status codes and error classification

Use when implementing database or RPC status-code extraction or changing
related error classification.

## Keep status capture independent of error classification

Capture available codes for success, warnings, expected negative outcomes, and
failures. A status code does not by itself require `error.type` or an error
span status. For example, an expected cache miss can have a code without being
classified as a failure.

Do not use success flags, an `ERR_` prefix, HTTP-like ranges, positivity, or
server provenance as universal availability or failure tests.

Adding status capture must preserve existing error classification, exception
fallback, span status, exception events, and metric grouping unless those
behaviors are intentionally changed.

A status code can also be an error type. An error type is not necessarily a
status code, so do not extract status from the final `error.type`.

## Follow the source's representation and availability rules

Follow the applicable system-specific convention, using
[semconv-conformance.md](semconv-conformance.md) to establish its version and mode.
Do not impose a universal numeric-only, enum-only, or literal-wire-value rule.
Library-normalized and synthetic statuses are not inherently invalid. For
example, gRPC's successful status is `OK`, numerically `0`.

Use bounded codes, not arbitrary messages, payloads, exception class names, or
`toString()` output. Do not invent a success code when the observed API exposes
none.

Establish availability from the supported client API. Zero and negative values
are not inherently absent. Retain type, parsing, and range validation.

Capture retained codes at completion even when result retrieval throws, times
out, or is cancelled.

## Select codes using each consumer's policy

When status capture and error classification use different eligibility rules,
select their values independently. Apply eligibility during selection so an
ineligible first value does not hide a later eligible value.

For SQLSTATE and vendor-code selection, prefer the more specific code when
established. Fall back to the other available code when the preferred code is
absent. Otherwise retain both as `SQLSTATE/vendorCode`. Use verified driver
semantics and canonical database identities, not SQL-dialect assumptions.

## Share extraction without hiding different behavior

Reuse identical extraction and remove pass-through wrappers. Keep adapters that
apply genuinely different classification rules.

Compare native numeric values before converting them to strings.

## Name values by their role

- Use `errorCode` or `expectedErrorCode` for a code shared by status capture and
  error-classification paths.
- Use `statusCode` for status-only values and `errorType` for classification-only
  values.
- Keep separate expectations when status and error type can differ.
- Leave semantic API getter names and telemetry attribute names unchanged.
