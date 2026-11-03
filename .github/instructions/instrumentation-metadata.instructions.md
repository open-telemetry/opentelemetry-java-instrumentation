---
applyTo: "instrumentation/**/metadata.yaml,instrumentation-docs/src/main/resources/shared-config-definitions.yaml"
---

# Instrumentation metadata

Compare configuration entries with the module's actual readers, including dependent common modules.
Keep names, types, defaults, and supported settings aligned with the code. Remove an unused setting
only after confirming it has no readers. General module enablement is represented by
`disabled_by_default`, not an ordinary configuration entry.

## Entry shape

| Field | Meaning |
| --- | --- |
| `name` | Flat property name; omit only for declarative-only settings |
| `declarative_name` | YAML path, required when there is no flat property |
| `type` | Flat-form `boolean`, `string`, `list`, `int`, or `map` |
| `description` | User-facing behavior |
| `default` | Value when unset; omit when absence falls back to another setting |
| `deprecated` | `true` exactly when the description starts with `Deprecated` |
| `replaced_by` | Documented replacement flat name, or declarative path if it has no flat form |
| `examples` | Optional module-specific non-obvious formats |
| `declarative_type` | Different YAML shape: `structured_list` or scalar `string`, `boolean`, `int` |
| `declarative_schema` | Per-item object schema for a structured list |

`replaced_by` belongs only on deprecated entries. Deprecated entries are excluded from generated
configuration examples.

Distinguish a real default from a nullable read that falls back to another setting. A module
override with no independent default omits `default` and explains its fallback in the description;
otherwise the generated YAML example can accidentally override that fallback.

## Shared settings

`instrumentation-docs/src/main/resources/shared-config-definitions.yaml` separates:

- `configurations`, reused by specific modules through `- ref: <id>`, including settings read by
  shared helpers such as `DbConfig` or `CommonConfig`;
- `global_configurations`, read on behalf of all instrumentation, such as semconv selection and
  span suppression, which the generator includes without module refs.

A setting read for specific modules is not global merely because its reader is in the
instrumentation API. Put a module-specific override immediately before the common `ref` it falls
back to. The override has no `default`.

## Structured and scalar declarative forms

`type` describes the flat form even when YAML is a list of objects. Set
`declarative_type: structured_list` and provide an object schema whose `required` keys are drawn
from `properties`. Properties have a type and optional description, default, and example.
The generated example uses each property's `example`, then `default`, then a name placeholder.
Supply examples where the placeholder would be unclear.

For example, a declarative-only rule list has no `name`:

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
      pattern:
        type: string
        example: '/users/\d+'
      template:
        type: string
        example: '/users/{id}'
      override:
        type: boolean
        default: false
```

For a scalar override, set `declarative_type` without an object schema. For example,
`otel.semconv-stability.opt-in` is a comma-separated flat `list` but the declarative
`general.stability_opt_in_list` is a `string`.

## Flat-to-declarative conversion

For ordinary `otel.instrumentation.*` names, the bridge removes that prefix, changes hyphens to
underscores, handles experimental markers, and prefixes the path with `java.`:

| Flat suffix | Declarative path |
| --- | --- |
| `grpc.emit-message-events` | `java.grpc.emit_message_events` |
| `grpc.experimental-span-attributes` | `java.grpc.experimental_span_attributes/development` |
| `logback-appender.experimental.capture-code-attributes` | `java.logback_appender.capture_code_attributes/development` |

A separate `experimental.` segment is removed; an `experimental-` compound prefix remains as
`experimental_`. Both place `/development` on the corresponding node.

`SPECIAL_MAPPINGS` handles names outside the mechanical rule, such as general HTTP header capture.
Consult `ConfigPropertiesBackedDeclarativeConfigProperties` rather than maintaining a second
mapping inventory. Flat lookup normalizes hyphens to dots, so an already-published YAML spelling may
work without another special mapping. Preserve it unless validation establishes a mapping gap.

An intentionally deprecated declarative spelling is documented only on an entry with
`deprecated: true`; current entries use the current supported spelling.

## Examples and validation

Add `examples` only for module-specific settings with non-obvious list, pattern, or enum formats,
not booleans, `general.*`, or `java.common.*`.

After editing metadata, run the focused round-trip/schema validation:

```bash
./gradlew :instrumentation-docs:test --tests DeclarativeConfigValidationTest
```

It checks flat-to-declarative lookup, types, schemas, and common-setting references. Keep the
documented type and default aligned with actual readers; round-trip conversion alone does not
prove that a setting is used.
