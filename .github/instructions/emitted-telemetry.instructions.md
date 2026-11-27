---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Emitted telemetry

Apply these rules when production code emits or modifies telemetry, including instrumentation,
SDK support, agent support, and extension modules.

## Applicable convention and effective mode

- Establish the adopted released convention from the instrumentation's telemetry contract, schema URL,
  or migration introducing its current shape. Resolve the effective mode from supported configuration
  and implementation. The semconv dependency version determines available constants, not which
  conventions the instrumentation adopted. If the target cannot be established, leave version-specific
  conclusions open.

- Identify the signal, protocol/domain, operation, and role. Read the complete applicable convention,
  including footnotes and referenced general rules. A key's presence in the attribute registry does
  not make it applicable to every signal; signal-specific rules can override general ones.

- If no convention applies, do not invent a requirement. Reuse a semconv attribute only when its name,
  type, and meaning match; otherwise use the general attribute-naming guidance and the repository's
  experimental telemetry conventions.

## Complete telemetry contract

- Compare observable output end to end:

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

- When attributing span or metric errors, keep a nested operation's server-error response and
  protocol code with that operation. Handle escaping callback exceptions under the outer client's
  failure and span-lifecycle policy, separately from its own response classification. Do not relay
  nested server-error confirmation just to classify the callback exception. For example, Redis
  `PING` receives `PONG`, then an application response builder issues an independent `GET` that
  receives `WRONGTYPE`. If the builder catches the exception, that failure alone does not fail
  `PING`. If it escapes, the outer call may fail during callback processing. Either way,
  `WRONGTYPE` belongs to `GET`, not to `PING`'s successful server response.

- Unless the applicable semantic convention says otherwise, prefer an available protocol failure code
  over a generic exception reporting that failure. Prefer useful typed exception classes for timeout,
  connection, and local-processing failures. Apply these choices consistently by failure category,
  not through per-code specificity checks. Fall back to the exception class when no applicable code is
  available. Record `db.response.status_code` independently.

## Requirement levels

- Evaluate whether an attribute should be included separately from the rules governing its value.
  An inclusion level such as `Recommended` or `Opt-In` does not weaken value requirements.

- Uppercase BCP 14 keywords have their defined force; lowercase words use their ordinary meaning.
  Treat feasible, unexplained departures from `SHOULD` or `RECOMMENDED` as departures from guidance,
  not `MUST` violations. Accept concrete constraints such as performance, security, privacy,
  unavailable source data, and compatibility when their consequences are accounted for.
  Use a concrete constraint to justify a departure from `SHOULD` or `RECOMMENDED`. Both choices under
  `MAY` or `OPTIONAL` are valid unless another requirement constrains them.

- Reserve claims of specification noncompliance for unmet `MUST`, `MUST NOT`, or `REQUIRED`.
  Describe a mismatch in a declared convention shape without such a keyword as a departure from that
  shape.

## Stability and safety

- Spans an instrumentation defines outside semantic conventions are experimental. Require an
  experimental opt-in for those spans. For example, a setting such as
  `otel.instrumentation.graphql.emit-experimental-telemetry` can enable GraphQL data-fetcher spans
  without affecting the GraphQL operation span.

- Attributes an instrumentation defines outside semantic conventions are experimental. Require
  opt-in through an experimental setting such as
  `otel.instrumentation.<name>.experimental-span-attributes`. If an experimental setting already
  gates the telemetry carrying those attributes, no separate opt-in is needed. This rule does not
  apply to application-supplied keys, such as those passed through `@SpanAttribute`.

- On telemetry that is already enabled, do not use generic switches such as
  `experimental-span-attributes`, `experimental-log-attributes`, or `emit-experimental-telemetry`
  to control capture of attributes defined by a semantic convention, including those marked
  `Development`. Follow the convention's opt-in guidance. If capture needs a separate switch, use a
  dedicated [`capture-*` setting](configuration.instructions.md#names-and-stability).

- Stable instrumentation does not emit unstable convention fields by default where opt-in is
  required. Check both convention-group and individual-field stability. Do not force migration to
  newer conventions or remove deprecated telemetry without checking supported compatibility modes
  and telemetry-stability guarantees.

- Use the bounded or low-cardinality value source the convention specifies. A semconv key does not
  make an unbounded value safe. Follow applicable sanitization, redaction, and privacy rules;
  `Recommended` is not permission to expose sensitive values. Do not invent universal normalization
  or redaction rules where the domain has its own.

## Constants

- Reuse semconv constants only when both key name and type match. Javaagent production code and
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

- Use the schema URL of the adopted semantic convention that defines the emitted span, metric, or
  log in its effective mode. Use the version adopted for that signal's convention, not the semconv
  dependency version. If no convention defines the signal, leave its schema URL unset. Supporting
  attributes cannot select, change, or invalidate the URL, and a parent span's schema does not
  establish a convention for its child. For example, a span defined by GenAI conventions uses the
  GenAI schema even when it carries RPC attributes. Custom controller, JDBC data-source, and servlet
  response method spans do not gain a schema from their parent or supporting attributes such as
  database, code, or exception attributes.

## Conformance evidence and coverage

- A conformance finding cites the exact requirement in the adopted semantic convention, describes
  the observable telemetry behavior, and explains the expected behavior or applicable exception.
  Do not infer a rule from memory or another convention version.

- Verify normative behavior with observable telemetry tests, including relevant conditional
  requirements, default/opt-in modes, domain-specific errors, and required redaction. There is no
  requirement for a unit test per convention field or an omission test solely because a feature is
  `MAY`.
