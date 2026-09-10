# [Semconv] Conformance requirement language

## Quick reference

- Use when: reviewing changes that implement or emit telemetry governed by semantic conventions,
  or when explicitly evaluating semantic-convention conformance
- Review focus: normative force, justified deviations, changed-line ownership, current
  conventions versus proposals

## Interpret normative keywords

OpenTelemetry defines uppercase `MUST`, `SHOULD`, `RECOMMENDED`, and related terms using
[BCP 14](https://www.rfc-editor.org/info/bcp14). Under
[RFC 2119 section 3](https://www.rfc-editor.org/rfc/rfc2119#section-3), `SHOULD` and
`RECOMMENDED` mean that a valid reason may justify a different course in a particular
circumstance, but the implementation must understand and carefully weigh the full implications.

Do not dismiss a deviation solely because the convention says `SHOULD` or `RECOMMENDED` instead
of `MUST`. Treat these terms as compelling guidance:

- Flag a changed-line deviation when the implementation can follow the guidance and the change
  supplies no concrete reason not to.
- Accept a deviation when a specific constraint justifies it and the implementation accounts for
  the consequences. Performance, security, privacy, unavailable source data, and compatibility
  can be valid reasons. Convenience, inherited behavior, or "not a MUST" alone are not.
- State that an unexplained `SHOULD` or `RECOMMENDED` deviation conflicts with or departs from the
  guidance. Reserve "non-compliant" or "violates the specification" for unmet `MUST`, `MUST NOT`,
  or `REQUIRED` requirements.
- Do not flag either permitted choice under `MAY` or `OPTIONAL` unless another requirement
  constrains that choice.

Only uppercase keywords have BCP 14 force. Lowercase words use their ordinary meaning.

## Separate requirement levels from value instructions

The semantic conventions define
[attribute requirement levels](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/)
that control whether instrumentation emits an attribute. For example, instrumentation should add
a `Recommended` attribute by default when it is readily and efficiently available, with documented
exceptions such as performance, security, or privacy.

An attribute's inclusion level does not weaken normative instructions about its value. Evaluate
both questions:

1. Should the instrumentation emit the attribute?
2. When emitted, does its value follow applicable `MUST`, `SHOULD`, and `RECOMMENDED`
   instructions?

## Keep findings in diff scope

An unchanged helper can cause newly changed code to depart from a semantic convention. Anchor the
finding on the changed line that newly exposes or relies on that behavior, and cite the unchanged
helper as evidence. Do not move the finding to an earlier pull request merely because that pull
request introduced the helper.

When reviewing against an open semantic-conventions proposal, apply its normative language as the
requested target but call it proposed guidance. Do not represent unmerged text as part of the
published conventions.

## Review wording

A useful finding identifies:

1. The exact semantic-convention requirement or recommendation.
2. The changed behavior that departs from it.
3. The observable consequence.
4. The expected behavior, unless the pull request documents a valid reason to diverge.

OpenTelemetry's
[notation and compliance rules](https://opentelemetry.io/docs/specs/otel/#notation-conventions-and-compliance)
define strict compliance in terms of `MUST`, `MUST NOT`, and `REQUIRED`. That compliance threshold
does not make `SHOULD` and `RECOMMENDED` too weak for review findings.
