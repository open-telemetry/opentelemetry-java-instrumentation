---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Emitted telemetry

Apply these rules when production code emits or modifies telemetry, including instrumentation,
SDK support, agent support, and extension modules.

## Applicable convention and effective mode

Establish the adopted released convention from the instrumentation's telemetry contract, schema URL,
or migration introducing its current shape. Resolve the effective mode from supported configuration
and implementation. The semconv dependency version determines available constants, not which
conventions the instrumentation adopted. If the target cannot be established, leave version-specific
conclusions open.

Identify the signal, protocol/domain, operation, and role. Read the complete applicable convention,
including footnotes and referenced general rules. A key's presence in the attribute registry does
not make it applicable to every signal; signal-specific rules can override general ones.

If no convention applies, do not invent a requirement. Reuse a semconv attribute only when its name,
type, and meaning match; otherwise use the general attribute-naming guidance and the repository's
experimental telemetry conventions.

## Complete telemetry contract

Compare observable output end to end:

- emission boundary and recording point;
- span, metric, event, and operation names;
- span kind and status;
- metric instrument kind, unit, aggregation meaning, and point attributes;
- attribute key, type, meaning, source, attachment point, and inclusion level;
- value construction, normalization, formatting, fallback, omissions, and well-known values;
- error classification, redaction, privacy, and cardinality constraints.

An exact contract field matters even when its generated table does not repeat `MUST`. Apply the
most specific domain rule instead of substituting general advice. Using the right constant alone
does not establish conformance.

## Error origin and operation ownership

When attributing span or metric errors, keep a nested operation's server-error response and
protocol code with that operation. Handle escaping callback exceptions under the outer client's
failure and span-lifecycle policy, separately from its own response classification. Do not relay
nested server-error confirmation just to classify the callback exception.

Unless the applicable semantic convention says otherwise, prefer an available protocol failure code
over a generic exception reporting that failure. Prefer useful typed exception classes for timeout,
connection, and local-processing failures. Apply these choices consistently by failure category,
not through per-code specificity checks. Fall back to the exception class when no applicable code is
available. Record `db.response.status_code` independently.

For example, Redis `PING` receives `PONG`, then an application response builder issues an independent
`GET` that receives `WRONGTYPE`. If the builder catches the exception, that failure alone does not
fail `PING`. If it escapes, the outer call may fail during callback processing. Either way,
`WRONGTYPE` belongs to `GET`, not to `PING`'s successful server response.

## Requirement levels

Evaluate whether an attribute should be included separately from the rules governing its value.
An inclusion level such as `Recommended` or `Opt-In` does not weaken value requirements.

Uppercase BCP 14 keywords have their defined force; lowercase words use their ordinary meaning.
Treat feasible, unexplained departures from `SHOULD` or `RECOMMENDED` as departures from guidance,
not `MUST` violations. Accept concrete constraints such as performance, security, privacy,
unavailable source data, and compatibility when their consequences are accounted for.
Use a concrete constraint to justify a departure from `SHOULD` or `RECOMMENDED`. Both choices under
`MAY` or `OPTIONAL` are valid unless another requirement constrains them.

Reserve claims of specification noncompliance for unmet `MUST`, `MUST NOT`, or `REQUIRED`.
Describe a mismatch in a declared convention shape without such a keyword as a departure from that
shape.

## Stability and safety

Stable instrumentation does not emit unstable convention fields by default where opt-in is
required. Check both convention-group and individual-field stability. Do not force migration to
newer conventions or remove deprecated telemetry without checking supported compatibility modes
and telemetry-stability guarantees.

Use the bounded or low-cardinality value source the convention specifies. A semconv key does not
make an unbounded value safe. Follow applicable sanitization, redaction, and privacy rules;
`Recommended` is not permission to expose sensitive values. Do not invent universal normalization
or redaction rules where the domain has its own.

## Constants

Reuse semconv constants only when both key name and type match. Javaagent production code and
tests may import stable and incubating constants directly. Library production code may import
matching stable constants. Copy incubating constants locally without adding an incubating dependency
to the published artifact.

The javaagent bundles its own dependencies, and test dependencies do not affect library consumers.

For example, library production code copies an incubating key locally:

```java
// copied from MessagingIncubatingAttributes
private static final AttributeKey<String> MESSAGING_SYSTEM_NAME =
    AttributeKey.stringKey("messaging.system.name");
```

## Schema URLs

Select the schema URL from the semantic convention defining the emitted span, metric, or log.
Its version follows the convention adopted by that signal and its effective mode, not the latest
semconv dependency. Registered attributes alone do not establish a signal
convention; supporting attributes must not select, change, or invalidate its schema.
If no semantic convention defines the emitted signal, leave its schema URL unset.

For example, GenAI operations use the GenAI schema despite supporting RPC attributes. Controller
spans have no defined convention, even when nested under an HTTP server span. Code or exception
attributes alone do not justify a schema URL on a custom operation.

## Conformance evidence and coverage

A conformance conclusion identifies the exact requirement in the adopted version, the changed
behavior, its observable consequence, and the expected behavior or justified exception. Do not
infer a rule from memory or another convention version.

Add observable telemetry tests for changed normative behavior, including relevant conditional
requirements, default/opt-in modes, domain-specific errors, and required redaction. There is no
requirement for a unit test per convention field or an omission test solely because a feature is
`MAY`.
