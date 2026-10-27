# [Semconv] Status codes and error classification

Use for database or RPC status-code work. Establish the applicable convention and
mode with [semconv-conformance.md](semconv-conformance.md).

## Separate status capture from error classification

Capture available codes for any outcome. A status code can also be an error type,
but an error type is not necessarily a status code. Do not derive codes from the
final `error.type`.

Status-only changes must preserve error classification, exception fallback,
span status/events, and metric grouping.

## Follow protocol and client semantics

Symbolic, normalized, synthetic, zero, and negative codes can be valid. Success
flags, `ERR_` prefixes, HTTP-like ranges, sign, and server provenance are not
universal availability or failure tests.

Validate types and ranges. Use bounded codes, not arbitrary messages, payloads,
exception classes, or `toString()` output. Do not invent missing codes.

Capture retained codes even when completion throws, times out, or is cancelled.

## Select and extract codes independently

Select status and error values independently when eligibility differs, skipping
ineligible candidates during selection.

For SQLSTATE/vendor codes, prefer the more specific code when known and fall back
to the other available code. Without a known preference, retain both available
values as `SQLSTATE/vendorCode`. Use canonical database identities, not SQL-dialect
assumptions.

Share identical extraction, retain adapters with different classification policies,
and compare native numeric values before string conversion.

## Name values by role

| Value | Name |
| --- | --- |
| Shared by status capture and error classification | `errorCode` or `expectedErrorCode` |
| Status only | `statusCode` |
| Classification only | `errorType` |

Keep separate expectations when values can differ. Leave semantic API getter names
and telemetry attribute names unchanged.
