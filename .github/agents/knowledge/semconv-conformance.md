# [Semconv] Semantic convention conformance

## Quick reference

- Use when: reviewing changes that implement or emit telemetry governed by semantic conventions,
  or when explicitly evaluating semantic-convention conformance
- Review focus: applicability, telemetry shape, requirement levels, value rules, safety, and
  stability

## Establish the review target

Determine the semantic-conventions version that the instrumentation targets. Start with
`semConvVersion` in `dependencyManagement/build.gradle.kts`, then account for the applicable
`otel.semconv-stability.opt-in` mode. Use the matching released documentation rather than silently
comparing released code with the latest unreleased conventions. See
[testing-semconv-stability.md](testing-semconv-stability.md) for this repository's modes.

Identify all dimensions of the applicable convention:

- signal, such as spans, metrics, events, logs, resources, or entities
- domain or protocol
- operation and role, such as client, server, producer, or consumer
- system-specific convention and any general convention it references

Read the complete applicable convention, including table footnotes and linked notes. The attribute
registry defines reusable keys and types; it does not establish that an attribute applies to every
signal that could use it. A signal-specific convention can also override the requirement level of
an imported attribute within its own scope.

See the
[semantic-conventions index](https://opentelemetry.io/docs/specs/semconv/)
and
[attribute requirement level rules](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/).

If no semantic convention applies, do not invent a conformance requirement. Prefer an existing
semantic-convention attribute only when its name, type, and meaning match exactly. Otherwise apply
the
[general attribute naming guidance](https://opentelemetry.io/docs/specs/semconv/general/naming/)
and this repository's experimental telemetry rules.

## Weigh normative language

OpenTelemetry defines uppercase `MUST`, `SHOULD`, `RECOMMENDED`, and related terms using
[BCP 14](https://www.rfc-editor.org/info/bcp14). Under
[RFC 2119 section 3](https://www.rfc-editor.org/rfc/rfc2119#section-3), `SHOULD` and
`RECOMMENDED` mean that a valid reason may justify a different course in a particular
circumstance, but the implementation must understand and carefully weigh the full implications.

Do not dismiss a deviation solely because the convention says `SHOULD` or `RECOMMENDED` instead
of `MUST`. Treat these terms as compelling guidance:

- Flag a deviation introduced by the change when the implementation can follow the guidance and
  the change supplies no concrete reason not to.
- Accept a deviation when a specific constraint justifies it and the implementation accounts for
  the consequences. Performance, security, privacy, unavailable source data, and compatibility
  can be valid reasons. Convenience or "not a MUST" alone are not.
- State that an unexplained `SHOULD` or `RECOMMENDED` deviation conflicts with or departs from the
  guidance. Reserve "non-compliant" or "violates the specification" for unmet `MUST`, `MUST NOT`,
  or `REQUIRED` requirements.
- Do not flag either permitted choice under `MAY` or `OPTIONAL` unless another requirement
  constrains that choice.

Only uppercase keywords have BCP 14 force. Lowercase words use their ordinary meaning.

OpenTelemetry's
[notation and compliance rules](https://opentelemetry.io/docs/specs/otel/#notation-conventions-and-compliance)
define strict compliance in terms of `MUST`, `MUST NOT`, and `REQUIRED`. That compliance threshold
does not make `SHOULD` and `RECOMMENDED` too weak for review findings.

## Check the complete telemetry contract

Semantic conventions define more than attribute keys. For every applicable group, check each
declared part of the observable telemetry:

- whether the signal should be emitted, and at what boundary or recording point
- exact span, metric, event, and operation names
- span kind and status behavior
- metric instrument kind, unit, aggregation meaning, and data-point attributes
- attribute key, type, meaning, source, attachment point, and requirement level
- value construction, normalization, formatting, fallback, and omission rules
- well-known or permitted enum values
- error classification and `error.type` behavior

An exact name, kind, unit, key, type, or well-known value declared by an applicable convention is
part of its contract even when a generated table does not repeat `MUST` in every row. Compare the
observable output end to end; using the correct semconv constant alone does not establish
conformance. When no uppercase keyword backs an exact contract field, describe a mismatch as a
departure from the declared convention shape rather than `MUST` noncompliance.

Apply the most specific applicable convention. Do not replace domain-specific rules for span
boundaries, names, status, errors, value sources, or conditional attributes with general advice.

## Apply attribute requirement levels

| Level                    | Review expectation                                                                                                                                                                                                     |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Required`               | The instrumentation must populate the attribute. It is on by default and cannot be disabled. Do not invent a general "not readily available" exception.                                                                |
| `Conditionally Required` | The instrumentation must populate the attribute whenever the convention's stated condition is true. When false, follow special instructions; if there are none and the value can be populated, it should be `Opt-In`.  |
| `Recommended`            | Emit it by default when it is readily and efficiently available. It may have a disable option. A concrete performance, security, privacy, or similar reason can justify default omission, with opt-in when applicable. |
| `Opt-In`                 | Emit it only when the user enables it. Instrumentation without configuration support must not emit it.                                                                                                                 |

An attribute's inclusion level does not weaken normative instructions about its value. Evaluate
both questions:

1. Should the instrumentation emit the attribute?
2. When emitted, does its value follow applicable `MUST`, `SHOULD`, and `RECOMMENDED`
   instructions?

## Protect telemetry quality and users

- For instrumentation-defined metric attributes outside the applicable convention, check
  cardinality before recommending a requirement level; attributes that may have high cardinality
  can only be `Opt-In`. When a convention already assigns a level, apply that declared level.
- Follow every applicable redaction, sanitization, sensitive-data, and privacy instruction. A
  `Recommended` attribute is not permission to expose sensitive values.
- Do not require expensive lookups or parsing by default when the requirement level permits
  omission or opt-in. The requirement-level guidance specifically accounts for performance,
  security, and privacy.
- Do not invent a universal normalization, redaction, or fallback rule. Cite the rule from the
  applicable convention.

## Account for stability

Check the stability of the applicable convention group as well as the individual telemetry fields:

- Stable instrumentation must not emit unstable convention fields by default. Use the repository's
  established opt-in mechanism where required. See
  [testing-experimental-flags.md](testing-experimental-flags.md).
- Do not require migration to newer conventions without checking OpenTelemetry telemetry-stability
  guarantees and this repository's compatibility mode.
- Deprecation does not make existing telemetry immediately invalid or removable. Verify the
  replacement and migration rules.

See
[semantic convention group stability](https://opentelemetry.io/docs/specs/semconv/general/semantic-convention-groups/)
and
[OpenTelemetry telemetry stability](https://opentelemetry.io/docs/specs/otel/telemetry-stability/).

## Use tests as evidence

OpenTelemetry does not require a unit test for every convention field. Ask for tests when they
protect changed normative behavior. Prefer observable telemetry assertions for the contract items
above, both branches of conditional requirements, default and opt-in modes, domain-defined error
behavior, and required redaction.

Do not request a test solely to prove omission of a `MAY` feature. Tie the requested test to a
specific convention rule.

## Write actionable findings

A useful finding identifies:

1. A link to the exact requirement or recommendation in the targeted convention version.
2. The changed behavior that departs from it.
3. The observable consequence.
4. The expected behavior, unless the pull request documents a valid reason to diverge.

Do not report a conformance finding that cannot be verified against the applicable convention.
Prefer silence over a requirement inferred from memory or a different convention version.
