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

### Deprecated Properties Under Common v3 Preview

Configuration names are user-facing API; also see
[Breaking Changes and Deprecation Policy](api-deprecation-policy.md). For ordinary replacement
properties that will be removed in 3.0, use this order:

1. Read the replacement first.
2. Only if the replacement is absent and v3 preview is disabled, read the deprecated value.
3. Warn only when returning/applying that deprecated value.
4. Under `otel.instrumentation.common.v3-preview=true`, silently ignore the deprecated value. The
   user explicitly opted into 3.0 behavior, so warning about a value that is not applied is noise.

Replacement-first lookup also supports warning-free rolling upgrades when many deployments share
centralized configuration. Operators can temporarily publish both names: old versions use the
deprecated property and ignore the replacement they do not recognize, while new versions use the
replacement and do not warn about the redundant deprecated property. After every deployment has
upgraded, operators can remove the deprecated property. A warning therefore identifies a deployment
that still depends on the deprecated value, not one merely carrying it for compatibility.

Instrumentation enablement name aliases are an exception. Enablement is resolved across an ordered
list of equivalent names, while deprecation warnings are based on whether the legacy alias is
explicitly configured. The replacement alias can therefore determine the result while the legacy
alias still triggers a warning. Under v3 preview, omit the legacy alias from resolution and silently
ignore its key. Do not copy this alias-specific warning behavior into ordinary property fallback.

Keep the deprecated read itself inside the `!v3Preview` branch, rather than reading it eagerly and
discarding it later:

```java
Boolean value = config.getBoolean("new_key");
if (value != null) {
  return value;
}
if (!v3Preview) {
  Boolean deprecatedValue = config.getBoolean("old_key");
  if (deprecatedValue != null) {
    warnOnce();
    return deprecatedValue;
  }
}
return defaultValue;
```

### Warning Text

Use the flat `otel.instrumentation.*` property name in warnings even when code reads the equivalent
declarative/YAML key. The dominant templates are:

```text
The <old-flat-property> setting and the equivalent declarative configuration property are
deprecated and will be removed in 3.0. Use <new-flat-property> or equivalent declarative
configuration instead.
```

```text
The '<old-flat-property>' system property is deprecated and will be removed in 3.0. Use
'<new-flat-property>' instead.
```

For an instrumentation-name alias, the concise form is:

```text
otel.instrumentation.<old>.enabled is deprecated; use
otel.instrumentation.<new>.enabled instead.
```

Only cite a declarative path directly when the old key never had a flat-property equivalent.

### Warning Deduplication

- Use a static `ConcurrentHashMap.newKeySet()` keyed by deprecated property when a helper handles
  multiple properties or can warn once per key.
- Use an `AtomicBoolean` for one deprecated property evaluated on a repeatable path.
- Omit explicit deduplication only when initialization guarantees one evaluation.

### CHANGELOG Categorization

The hand-written property deprecation entry belongs under `🚫 Deprecations`. The release-note
generator assigns one section per pull request and always classifies configuration-property renames
as deprecations, so the generated deprecation bullet may also mention that v3 preview ignores the
legacy property. Do not request a separate hand-written or generated enhancement entry.

## Migration Support Without Major-Preview Removal (Optional)

For a deprecated property that is not being removed by the active major-version preview, code may
read both old and new names. Do not use this unconditional fallback for properties being removed by
the preview; use the guarded pattern above instead.

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

**Deprecated properties under v3 preview:**

- **Deprecated value read before or outside the `!v3Preview` guard**: keep the read inside the
  compatibility branch so 3.0 behavior does not observe the legacy setting.
- **Warning emitted for a value ignored under v3 preview**: warn only when the deprecated value is
  actually applied; v3-preview users should get silent 3.0 behavior.
- **Warning emitted when the replacement already determines an ordinary property value**: do not
  warn merely because shared configuration carries both names during a mixed-version rollout. This
  does not apply to instrumentation enablement name aliases.
- **Missing warning deduplication on a repeatable path**: use a per-key concurrent set for multiple
  properties or an `AtomicBoolean` for a single property.
- **Configuration-property rename classified outside deprecations**: generated release notes assign
  the pull request to `🚫 Deprecations`, including any v3-preview behavior described by its bullet.
