# [Config] Configuration Property Stability and Breaking Changes

## Quick Reference

- Use when: reviewing configuration property definitions, stability, or deprecation
- Review focus: stable vs experimental policy, deprecation communication, naming conventions

## How Configuration Is Read

Instrumentation code reads configuration through the **declarative config API**
(`DeclarativeConfigProperties`), accessed via `DeclarativeConfigUtil`:

```java
DeclarativeConfigProperties config =
    DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "grpc");
boolean emitEvents = config.getBoolean("emit_message_events", true);
List<String> metadata = config.get("capture_metadata").get("client")
    .getScalarList("request", String.class, emptyList());
```

Users can supply values via **flat properties** (system properties, env vars) or **declarative
YAML**. The bridge `ConfigPropertiesBackedDeclarativeConfigProperties` translates flat
properties into the declarative tree automatically, so instrumentation code uses one API
regardless of the source.

**Flat `ConfigProperties` is only used directly** in `AgentDistributionConfig` for
instrumentation enable/disable bootstrapping (`otel.instrumentation.<name>.enabled`,
`otel.instrumentation.common.default-enabled`). All other config reads go through the
declarative API.

### Two User-Facing Surfaces

| Surface       | Experimental marker             | Stable marker                 |
| ------------- | ------------------------------- | ----------------------------- |
| Flat property | word `experimental` in the name | no `experimental` in the name |
| YAML key      | `/development` suffix           | no suffix                     |

The bridge translates between them (underscores ↔ hyphens, `/development` ↔ `experimental.`
prefix, `SPECIAL_MAPPINGS` for legacy renames that don't follow the mechanical rule).

## The Two Tiers of Stability

Defined in [VERSIONING.md](../../../VERSIONING.md):

| Tier             | Flat property pattern                                           | YAML key pattern          | Breaking changes allowed?                     |
| ---------------- | --------------------------------------------------------------- | ------------------------- | --------------------------------------------- |
| **Stable**       | No `experimental` in name, not under `otel.javaagent.testing.*` | No `/development` suffix  | ❌ Deprecate in minor, **remove only in 3.0** |
| **Experimental** | Contains `experimental` anywhere                                | Has `/development` suffix | ✅ Deprecate in one release, remove in next   |

`otel.javaagent.testing.*` — always allowed to break, regardless of marker.

Examples (flat ↔ YAML):

- `otel.instrumentation.http.client.capture-request-headers` ↔ `request_captured_headers` — **stable**
- `otel.instrumentation.common.db.experimental.sqlcommenter.enabled` ↔ `sqlcommenter/development: { enabled: true }` — **experimental**
- `otel.instrumentation.http.client.emit-experimental-telemetry` ↔ `emit_experimental_telemetry/development: true` — **experimental**

## Deprecation Communication

Config properties have no `@Deprecated` annotation and no automatic forwarding. Deprecation
must be communicated through:

1. A `🚫 Deprecations` CHANGELOG entry naming the old and new property.
2. A comment in code near where the old property is read.
3. **A `WARN`-level log message at startup** if the deprecated property is detected.
   The warning message should reference the **flat system property name**
   (`otel.instrumentation.…`) since that is what most users configure today:

   ```java
   boolean oldSetting = config.getBoolean("old_setting/development", false);
   if (oldSetting) {
     logger.warning(
         "The otel.instrumentation.<module>.experimental.old-setting setting is"
             + " deprecated and will be removed in a future version.");
   }
   ```

   Note: the code reads via the declarative config API (YAML key), but the warning cites the
   flat property name for user clarity.

## Migration Support Pattern (Optional)

During the deprecation window, code may read both old and new names:

```java
// Using the declarative config API
Boolean value = config.getBoolean("new_property_name");
if (value == null) {
  value = config.getBoolean("old_property_name");
}
```

## Naming Conventions

| Rule                | Flat property                                                      | YAML key                                                          |
| ------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------- |
| Prefix              | `otel.instrumentation.<module>.` or `otel.instrumentation.common.` | Under `instrumentation/development → java → <module>` or `common` |
| Word separator      | hyphens (kebab-case)                                               | underscores (snake_case)                                          |
| Experimental marker | `experimental` in name                                             | `/development` suffix                                             |
| Boolean toggle      | `.enabled` suffix                                                  | `enabled` leaf key                                                |
| Env var form        | dots/hyphens → ALL_CAPS underscores                                | N/A                                                               |

### Structured Selector Names

A structured selector object with `included` and/or `excluded` lists is named after the resource
being selected, for example `mdc_attributes`, `headers`, or `request_parameters`. This follows the
OpenTelemetry Configuration
[`IncludeExclude`](https://github.com/open-telemetry/opentelemetry-configuration/blob/main/schema/common.yaml)
shape and its noun-based uses such as `attribute_keys` and `resource_constant_labels`.

Do not give a new structured selector parent a `capture_*` name. A path such as
`capture_mdc_attributes.excluded` is contradictory: the parent describes a selection, while the
leaf excludes values from that selection. Retain `capture_*` for action booleans that collect or
emit data that would otherwise be absent, such as `capture_query` and
`capture_message_content`.

```yaml
# Preferred
mdc_attributes:
  included: [trace_id, request_id]
  excluded: [password]

# Avoid
capture_mdc_attributes:
  excluded: [password]
```

Java methods that configure a structured selector are also named after the selected resource,
without `Capture` or `Captured`, for example `setMdcAttributes(IncludeExclude)`. Use one selector
value instead of paired include/exclude setters, with the shared `IncludeExclude` selector type as
the intended end state. Retain `setCapture*` for action booleans, such as
`setCaptureQuery(boolean)`.

### Structured Selector Defaults

What a selector means when it is absent depends on whether the setting it controls is
none-by-default or all-by-default. Both baselines use the same shape and matching rules.

For a none-by-default setting, the selector doubles as the on switch. An absent selector selects
nothing, and an exclude-only selector selects everything except the excluded values. Document
select-all as `included: ["*"]`, or `included=*` in flat configuration:

```yaml
# select everything except one value
mdc_attributes:
  excluded: [password]

# select everything
mdc_attributes:
  included: ["*"]
```

For an all-by-default setting, the selector filters telemetry that is already emitted. An absent
selector keeps everything, an omitted `included` list keeps everything not excluded, and
exclude-only is the natural shape.

An empty selector, one with no patterns in either list, carries no configuration, so treat it the
same as an absent selector and fall back to the setting's own default. This keeps an empty selector
a no-op and matches flat configuration, where empty property values cannot be distinguished from
unset ones. `IncludeExclude#isEmpty()` identifies that case.

### Documenting Structured Selectors

Describe the relationship between the two lists as precedence, not as order of application.
"Exclusions are applied after inclusions" reads as a sequence and leaves it ambiguous whether a
later rule can re-add a value that an earlier rule removed. State the outcome instead, using the
same sentence everywhere:

```text
Excluded patterns take precedence over included patterns.
```

Document the matching rules on every surface a user reads: the property table, the metadata
description, and the javadoc of the Java setter. Cover pattern syntax, case sensitivity, precedence,
and what an absent selector means, because a reader on IDE hover does not see the README.

## Structured Config (YAML-Only)

Some configurations require structured data only expressible in YAML:

- **Structured lists** (`getStructuredList()`): e.g. `url_template_rules` (pattern/template/override
  objects), `service_peer_mapping` (with `service_namespace` not available via flat property)
- **Distribution config** (`distribution.javaagent`): `instrumentation` block with
  `default_enabled`, `enabled`/`disabled` lists — deserialized into `AgentDistributionConfig`
- **`ComponentProvider` components**: YAML nodes matched by `getName()` (snake_case),
  discovered via `@AutoService(ComponentProvider.class)`

These have no flat-property fallback, so tests must cover declarative config mode.

## What to Flag in Review

**Stability violations:**

- **Stable property/key removed in a minor release**: cannot be removed before 3.0.
- **Stable property/key deprecated without a CHANGELOG entry**: `🚫 Deprecations` entry required.
- **Stable property/key renamed in a single PR** (old removed, new added): old must remain
  (deprecated) until 3.0.
- **Zero deprecation window** (deprecated and removed in same PR): needs strong justification.

**Experimental marker issues:**

- **Experimental feature without marker**: flat property must contain `experimental`; YAML key
  must have `/development` suffix. Both must agree.
- **Stable feature with marker**: don't use `experimental` / `/development` on features
  intended to be stable — it misleads users about the guarantee.

**Naming / mapping issues:**

- **Property name doesn't follow conventions** (kebab-case flat, snake_case YAML, correct prefix).
- **`SPECIAL_MAPPINGS` not updated after rename**: the bridge will resolve the old YAML path to
  a stale flat property.
- **`ComponentProvider.getName()` mismatch**: must exactly match the YAML node name (snake_case).

**Declarative config correctness:**

- **Missing default values in declarative config reads**: always provide defaults
  (`getBoolean(name, default)`, etc.) for graceful degradation when YAML is unavailable.
- **Wrong config scope**: `getInstrumentationConfig(ot, name)` → `java → <name>`;
  `getGeneralInstrumentationConfig(ot)` → `general`. HTTP header capture lives under `general`.
