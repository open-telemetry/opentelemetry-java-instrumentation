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

| Surface | Experimental marker | Stable marker |
| --- | --- | --- |
| Flat property | word `experimental` in the name | no `experimental` in the name |
| YAML key | `/development` suffix | no suffix |

The bridge translates between them (underscores ↔ hyphens, `/development` ↔ `experimental.`
prefix, `SPECIAL_MAPPINGS` for legacy renames that don't follow the mechanical rule).

## The Two Tiers of Stability

Defined in [VERSIONING.md](../../../VERSIONING.md):

| Tier | Flat property pattern | YAML key pattern | Breaking changes allowed? |
| --- | --- | --- | --- |
| **Stable** | No `experimental` in name, not under `otel.javaagent.testing.*` | No `/development` suffix | ❌ Deprecate in minor, **remove only in 3.0** |
| **Experimental** | Contains `experimental` anywhere | Has `/development` suffix | ✅ Deprecate in one release, remove in next |

`otel.javaagent.testing.*` — always allowed to break, regardless of marker.

Examples (flat ↔ YAML):

- `otel.instrumentation.http.client.capture-request-headers` ↔ `request_captured_headers` — **stable**
- `otel.instrumentation.common.experimental.db-sqlcommenter.enabled` ↔ `sqlcommenter/development: { enabled: true }` — **experimental**
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
   boolean captureEventName = config.getBoolean("capture_event_name/development", false);
   if (captureEventName) {
     logger.warning(
         "The otel.instrumentation.logback-appender.experimental.capture-event-name setting is"
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

| Rule | Flat property | YAML key |
| --- | --- | --- |
| Prefix | `otel.instrumentation.<module>.` or `otel.instrumentation.common.` | Under `instrumentation/development → java → <module>` or `common` |
| Word separator | hyphens (kebab-case) | underscores (snake_case) |
| Experimental marker | `experimental` in name | `/development` suffix |
| Boolean toggle | `.enabled` suffix | `enabled` leaf key |
| Env var form | dots/hyphens → ALL_CAPS underscores | N/A |

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
