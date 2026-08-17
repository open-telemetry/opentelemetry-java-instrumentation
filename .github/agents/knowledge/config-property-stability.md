# [Config] Configuration Property Stability and Breaking Changes

## Quick Reference

- Use when: reviewing configuration property definitions, stability, or deprecation
- Review focus: stable vs unstable property policy, deprecation communication, naming conventions

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

| Surface       | Unstable marker                                           | Stable marker                         |
| ------------- | --------------------------------------------------------- | ------------------------------------- |
| Flat property | word `experimental` or `preview` in the name              | neither `experimental` nor `preview`  |
| YAML key      | `/development` suffix or word `experimental` or `preview` | no suffix and neither word in the key |

The bridge translates underscores ↔ hyphens and `/development` ↔ the `experimental.` prefix;
`experimental` and `preview` can also remain part of a name. `SPECIAL_MAPPINGS` handles legacy
renames that don't follow the mechanical rule.

## The Two Tiers of Stability

Defined in [VERSIONING.md](../../../VERSIONING.md):

| Tier         | Flat property pattern                                                | YAML key pattern                                 | Breaking changes allowed?                     |
| ------------ | -------------------------------------------------------------------- | ------------------------------------------------ | --------------------------------------------- |
| **Stable**   | No `experimental` or `preview`, not under `otel.javaagent.testing.*` | No `/development`, `experimental`, or `preview`  | ❌ Deprecate in minor, **remove only in 3.0** |
| **Unstable** | Contains `experimental` or `preview` anywhere                        | Has `/development`, `experimental`, or `preview` | ✅ Deprecate in one release, remove in next   |

`otel.javaagent.testing.*` — always allowed to break, regardless of marker.

A stable property name remains a stable surface even when the code reading it is alpha: users
configure the javaagent, which is published as a stable artifact, so its user-facing names outlive
the alpha classes behind them. A deprecated *stable property name* and a deprecated *Java method*
introduced by the same PR therefore often have different removal timelines.

Examples:

- `otel.instrumentation.http.client.capture-request-headers` ↔ `request_captured_headers` — **stable**
- `otel.instrumentation.common.db.experimental.sqlcommenter.enabled` ↔ `sqlcommenter/development: { enabled: true }` — **experimental**
- `otel.instrumentation.http.client.emit-experimental-telemetry` ↔ `emit_experimental_telemetry/development: true` — **experimental**
- `otel.instrumentation.common.v3-preview` — **preview**

## Deprecation Communication

Config properties have no `@Deprecated` annotation and no automatic forwarding. Deprecation
must be communicated through:

1. A `🚫 Deprecations` CHANGELOG entry naming the old and new property, including instrumentation
   enablement alias renames.
2. A comment in code near where the old property is read.
3. **A `WARN`-level log message at startup** when the deprecated property is applied, naming the old
   and replacement flat properties, or the declarative paths when there is no flat form. Exact
   wording is not standardized. Deduplicate with a static `AtomicBoolean` for a single property or a
   static keyed set for several, unless initialization guarantees one evaluation. Instrumentation
   enablement aliases are the exception described below.

### Migration Support Pattern

Configuration names are user-facing API; see also
[Breaking Changes and Deprecation Policy](api-deprecation-policy.md). Read the replacement first,
fall back to the deprecated name only when the replacement is absent, and warn only when the
deprecated value is actually applied:

```java
Boolean value = config.getBoolean("new_key");
if (value != null) {
  return value;
}
if (!v3Preview) { // stable names only; see below
  Boolean deprecatedValue = config.getBoolean("old_key");
  if (deprecatedValue != null) {
    warnOnce();
    return deprecatedValue;
  }
}
return defaultValue;
```

The nullable reads are intentional: migration code must detect absence before applying the default.
Keep the deprecated read inside the `!v3Preview` branch rather than reading it eagerly and
discarding it later.

Replacement-first lookup also supports warning-free upgrades with centralized configuration:
operators can publish both names while old versions use the deprecated property and new versions use
the replacement, then remove the deprecated property after rollout. A warning then identifies a
deployment that still depends on the deprecated value, not one merely carrying it for compatibility.

The `!v3Preview` guard applies only to **stable** property names, whose deprecated spelling must
survive until 3.0. Preview mode must reproduce 3.0 behavior: 3.0 will no longer know about the
deprecated property, so it will not read, apply, or warn about it, while preceding minor releases
still warn outside preview mode. An `experimental`- or `preview`-marked property can be removed in
the next minor release, so there is nothing for preview mode to reproduce: drop the guard, the
`v3Preview` parameter, and the v3-preview test variant, and keep only the warning.

These hypothetical deprecated names illustrate the two treatments:

| Hypothetical deprecated property                         | Marker         | Treatment                                 |
| -------------------------------------------------------- | -------------- | ----------------------------------------- |
| `otel.instrumentation.<module>.old-setting`              | none (stable)  | v3-preview gated, removed in 3.0          |
| `otel.instrumentation.<module>.experimental.old-setting` | `experimental` | ungated, may be removed in the next minor |
| `otel.instrumentation.<module>.preview-old-setting`      | `preview`      | ungated, may be removed in the next minor |

Instrumentation enablement name aliases are an exception. Enablement resolves across an ordered list
of equivalent names, while the warning is driven by explicit legacy-key presence, so the replacement
alias can determine the result while the legacy alias still warns. Under v3 preview the legacy alias
is dropped from resolution and its key silently ignored. Do not copy this behavior into ordinary
property fallback.

## Naming Conventions

| Rule            | Flat property                                                      | YAML key                                                          |
| --------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------- |
| Prefix          | `otel.instrumentation.<module>.` or `otel.instrumentation.common.` | Under `instrumentation/development → java → <module>` or `common` |
| Word separator  | hyphens (kebab-case)                                               | underscores (snake_case)                                          |
| Unstable marker | `experimental` or `preview` in name                                | `/development` suffix or `experimental` or `preview` in name      |
| Boolean toggle  | `.enabled` suffix                                                  | `enabled` leaf key                                                |
| Env var form    | dots/hyphens → ALL_CAPS underscores                                | N/A                                                               |

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
select-all as `included: ["*"]`, or `included=*` in flat configuration, because flat configuration
cannot express a present selector that omits its included patterns:

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

**Unstable marker issues:**

- **Experimental feature without marker**: flat property must contain `experimental`; YAML key
  must have `/development` suffix. Both must agree.
- **Preview property treated as stable**: names containing `preview` have the same breaking-change
  exemption as names containing `experimental`.
- **Stable feature with marker**: don't use `experimental`, `preview`, or `/development` on
  features intended to be stable — it misleads users about the guarantee.

**Naming / mapping issues:**

- **Property name doesn't follow conventions** (kebab-case flat, snake_case YAML, correct prefix).
- **`SPECIAL_MAPPINGS` not updated after rename**: the bridge will resolve the old YAML path to
  a stale flat property.
- **`ComponentProvider.getName()` mismatch**: must exactly match the YAML node name (snake_case).

**Declarative config correctness:**

- **Missing default values in declarative config reads**: provide defaults
  (`getBoolean(name, default)`, etc.) for graceful degradation when YAML is unavailable. Migration
  probes are the exception: use the nullable overload to detect absence, then apply the default
  after checking replacement and deprecated names.
- **Wrong config scope**: `getInstrumentationConfig(ot, name)` → `java → <name>`;
  `getGeneralInstrumentationConfig(ot)` → `general`. HTTP header capture lives under `general`.

**Deprecated properties under v3 preview:**

- **`experimental`- or `preview`-marked property gated on v3 preview**: the gate only exists to
  reproduce 3.0 behavior for names that must survive until 3.0. An unstable name needs the warning
  and nothing else.
- **Stable deprecated property value read or warned about under v3 preview**: preview must neither
  observe nor warn about a setting that 3.0 will not recognize.
- **Warning emitted when the replacement already determines an ordinary property value**: do not
  warn merely because shared configuration carries both names during a mixed-version rollout. This
  does not apply to instrumentation enablement name aliases.
- **Missing warning deduplication on a repeatable path**: use a static `AtomicBoolean` for a single
  property or a per-key concurrent set for several.
