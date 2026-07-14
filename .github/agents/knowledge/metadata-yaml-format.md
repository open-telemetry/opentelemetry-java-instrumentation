# [Config] metadata.yaml Format and Declarative Name Conversion

## Quick Reference

- Use when: reviewing or creating `metadata.yaml` files, converting config names
- Review focus: declarative_name format, examples guidelines, special mappings, config validation

## Entry Structure

Each configuration entry includes:

- `name`: Flat system property (e.g., `otel.instrumentation.grpc.emit-message-events`). Optional
  only for declarative-only configs that have no flat property (see Structured Lists); such entries
  MUST have a `declarative_name`.
- `declarative_name`: YAML key path (e.g., `java.grpc.emit_message_events`)
- `type`: `boolean`, `string`, `list`, `int`, `map`. Describes the **flat** form.
- `description`: Human-readable explanation
- `default`: Default value
- `examples` (optional): Only for module-specific configs with non-obvious format
- `declarative_type` (optional): Overrides the declarative-form shape when it differs from the flat
  `type`. Currently only `structured_list` (see Structured Lists).
- `declarative_schema` (optional): Per-item object schema, required when
  `declarative_type: structured_list` (see Structured Lists).

## Structured Lists

Some declarative configs are **lists of objects** even though their flat form is a scalar/map. The
flat `type` describes the flat system property; `declarative_type: structured_list` plus a
`declarative_schema` describe the per-item object shape for the declarative builder. The schema
mirrors the JSON-schema style used by opentelemetry-configuration: `type: object`, a `required`
list, and named `properties` (each with `type`, optional `description`, optional `default`). The
`required` keys must be a subset of `properties`.

`service_peer_mapping` — flat form is a `host=service` map, declarative form is a list of
`{peer, service_name}`:

```yaml
- name: otel.instrumentation.common.peer-service-mapping
  declarative_name: java.common.service_peer_mapping
  description: Used to specify a mapping from host names or IP addresses to peer services.
  type: map
  default: ""
  declarative_type: structured_list
  declarative_schema:
    type: object
    required: [peer, service_name]
    properties:
      peer: { type: string, description: Host name or IP address to match against. }
      service_name: { type: string, description: Peer service name to record for matching peers. }
```

`url_template_rules` is **declarative-only** (no flat property) — it omits `name`:

```yaml
- declarative_name: java.common.http.client.url_template_rules
  description: Rules for deriving low-cardinality URL templates from HTTP client request URLs.
  type: list
  default: ""
  declarative_type: structured_list
  declarative_schema:
    type: object
    required: [pattern, template]
    properties:
      pattern: { type: string }
      template: { type: string }
      override: { type: boolean, default: false }
```

## Special Mappings

Non-standard mappings (see `ConfigPropertiesBackedDeclarativeConfigProperties.java` for latest):

| Flat Property                                                                   | Declarative YAML Key                                              |
| ------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| `otel.instrumentation.http.client.capture-request-headers`                      | `general.http.client.request_captured_headers`                    |
| `otel.instrumentation.http.client.capture-response-headers`                     | `general.http.client.response_captured_headers`                   |
| `otel.instrumentation.http.server.capture-request-headers`                      | `general.http.server.request_captured_headers`                    |
| `otel.instrumentation.http.server.capture-response-headers`                     | `general.http.server.response_captured_headers`                   |
| `otel.instrumentation.sanitization.url.experimental.sensitive-query-parameters` | `general.sanitization.url.sensitive_query_parameters/development` |
| `otel.semconv-stability.opt-in`                                                 | `general.semconv_stability.opt_in`                                |
| `otel.instrumentation.http.known-methods`                                       | `java.common.http.known_methods`                                  |
| `otel.instrumentation.http.client.emit-experimental-telemetry`                  | `java.common.http.client.emit_experimental_telemetry/development` |
| `otel.instrumentation.http.server.emit-experimental-telemetry`                  | `java.common.http.server.emit_experimental_telemetry/development` |
| `otel.instrumentation.messaging.experimental.receive-telemetry.enabled`         | `java.common.messaging.receive_telemetry/development.enabled`     |
| `otel.instrumentation.messaging.experimental.capture-headers`                   | `java.common.messaging.capture_headers/development`               |
| `otel.instrumentation.genai.capture-message-content`                            | `java.common.gen_ai.capture_message_content`                      |
| `otel.instrumentation.experimental.span-suppression-strategy`                   | `java.common.span_suppression_strategy/development`               |
| `otel.instrumentation.opentelemetry-annotations.exclude-methods`                | `java.opentelemetry_extension_annotations.exclude_methods`        |
| `otel.experimental.javascript-snippet`                                          | `java.servlet.javascript_snippet/development`                     |
| `otel.jmx.enabled`                                                              | `java.jmx.enabled`                                                |
| `otel.jmx.config`                                                               | `java.jmx.config`                                                 |
| `otel.jmx.target.system`                                                        | `java.jmx.target.system`                                          |

## Standard Conversion

For `otel.instrumentation.*` properties not in SPECIAL_MAPPINGS:

1. Strip `otel.instrumentation.` prefix
2. Replace `-` with `_` (kebab-case → snake_case)
3. Handle `experimental`:
   - `experimental.` as a separate path segment → **remove** it, add `/development` to next segment
   - `experimental-` as a prefix within a segment name → **keep** it (as `experimental_`), add `/development` to that segment
4. Prepend `java.`

Examples:

| Flat Property                                                                | Declarative YAML Key                                        |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `otel.instrumentation.grpc.emit-message-events`                              | `java.grpc.emit_message_events`                             |
| `otel.instrumentation.grpc.experimental-span-attributes`                     | `java.grpc.experimental_span_attributes/development`        |
| `otel.instrumentation.aws-sdk.experimental-span-attributes`                  | `java.aws_sdk.experimental_span_attributes/development`     |
| `otel.instrumentation.logback-appender.experimental.capture-code-attributes` | `java.logback_appender.capture_code_attributes/development` |
| `otel.instrumentation.common.experimental.controller-telemetry.enabled`      | `java.common.controller_telemetry/development.enabled`      |

**Key distinction**:

- `experimental-span-attributes` (hyphenated compound) → `experimental_span_attributes/development` (prefix **kept**)
- `experimental.capture-code-attributes` (separate segment) → `capture_code_attributes/development` (segment **removed**)

## Examples Guidelines

Add `examples` only for module-specific configs with non-obvious format (lists, patterns, enums).

**Never add for**: `general.*`, `java.common.*`, or boolean configs.

```yaml
- name: otel.instrumentation.grpc.capture-metadata.client.request
  declarative_name: java.grpc.capture_metadata.client.request
  type: list
  examples:
    - "custom-request-header"
    - "header1,header2,header3"
```

## Validation Procedure

### 1. Validate experimental markers match

- `/development` in declarative_name ↔ `experimental` in flat name (MUST match both ways)
- Do not rename an existing, published `declarative_name` solely to make it match the mechanical
  conversion rule. Flat property lookup normalizes `-` to `.`, so a legacy YAML key such as
  `java.aws_sdk.use_propagator_for_messaging/development` can still resolve to
  `otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging` without a special
  mapping. Let the automated validation decide whether a `SPECIAL_MAPPINGS` entry is actually
  needed.
- WRONG: `otel.instrumentation.servlet.capture-request-parameters` + `java.servlet.capture_request_parameters/development`
- RIGHT: `otel.instrumentation.servlet.experimental.capture-request-parameters` + `java.servlet.capture_request_parameters/development`

### 2. Verify config is used

Search module source for `DeclarativeConfigUtil.getInstrumentationConfig()`, `config.getBoolean()`, etc.

If a module has a dependency on other modules (for example, a "-common" module, check those as well since they may read the config.

### 3. Verify type and default

Match type and default value with actual code usage.

## Automated Test (MANDATORY)

**Run after any metadata.yaml changes:**

```bash
./gradlew :instrumentation-docs:test --tests DeclarativeConfigValidationTest
```

This validates round-trip conversion (flat property → bridge → declarative path) and catches:

- Wrong flat property names (e.g., missing `experimental.`)
- Wrong declarative paths
- Type mismatches

Example failure when flat name is wrong:

```
FAIL in ../instrumentation/liberty/liberty-20.0/metadata.yaml:
  flat property: otel.instrumentation.servlet.capture-request-parameters
  expected: [item1, item2, item3]
  got: null
```

**Test must pass before completion.**

## Validation Outcomes

| Issue                        | Action                                  |
| ---------------------------- | --------------------------------------- |
| Config not used              | Flag for removal                        |
| Default/type mismatch        | Update metadata.yaml to match code      |
| Missing config (in code)     | Add to metadata.yaml                    |
| Experimental marker mismatch | Fix flat and declarative names to agree |

## Output Format

```yaml
- name: otel.instrumentation.grpc.emit-message-events
  declarative_name: java.grpc.emit_message_events
  type: boolean
  description: Determines whether to emit a span event for each message.
  default: true
- name: otel.instrumentation.grpc.experimental-span-attributes
  declarative_name: java.grpc.experimental_span_attributes/development
  type: boolean
  description: Enable capture of experimental span attributes.
  default: false
```

## Edge Cases

- Properties without `otel.instrumentation.` prefix → check SPECIAL_MAPPINGS
- Already has declarative_name → skip conversion unless the automated validation fails; preserve
  existing user-facing YAML keys and add a `SPECIAL_MAPPINGS` bridge entry only when normalized flat
  property lookup cannot resolve the existing name
