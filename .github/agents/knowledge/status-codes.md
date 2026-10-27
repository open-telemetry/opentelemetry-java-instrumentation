# [Semconv] Status codes and error classification

Use for database or RPC status-code work. Establish the applicable convention and
mode with [semconv-conformance.md](semconv-conformance.md).

- Capture available codes for any outcome, independently of failure classification.
  Success flags, `ERR_` prefixes, HTTP-like ranges, sign, and server provenance are
  not universal availability or failure tests.
- Follow protocol and client semantics. Symbolic, normalized, synthetic, zero, and
  negative codes can be valid. Validate types and ranges. Use bounded codes, not
  arbitrary messages, payloads, exception classes, or `toString()` output. Do not
  invent missing codes.
- A status code can be an error type, but not every error type is a status code.
  Do not derive codes from the final `error.type`. Status-only changes must preserve
  error classification, exception fallback, span status/events, and metric grouping.
- Capture retained codes even when completion throws, times out, or is cancelled.
  Select status and error values independently when eligibility differs, skipping
  ineligible candidates during selection.
- For SQLSTATE/vendor codes, prefer the more specific code when known and fall back
  to the other available code. Without a known preference, retain both available
  values as `SQLSTATE/vendorCode`. Use canonical database identities, not SQL-dialect
  assumptions.
- Share identical extraction, retain adapters with different classification policies,
  and compare native numeric values before string conversion.
- Name shared codes `errorCode` or `expectedErrorCode`, status-only values `statusCode`,
  and classification-only values `errorType`. Keep separate expectations when values
  can differ. Leave semantic API getter names and telemetry attribute names unchanged.
