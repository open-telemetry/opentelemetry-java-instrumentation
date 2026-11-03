---
applyTo: "**"
---

# User-facing configuration

Apply these rules when defining, mapping, reading, or documenting user-facing configuration,
regardless of where its implementation lives. Build-only and maintenance settings are not
user-facing instrumentation configuration.

## Names and stability

- Check both the flat property and declarative YAML form of an `otel.instrumentation.*` setting.
  New flat-property segments use kebab-case; new YAML keys use snake_case.
- For a new experimental feature, use an `experimental` marker in the flat name and the
  corresponding `/development` YAML form. Preview flat names use a `preview` marker and an
  unstable YAML form, either `/development` or `preview` in the key. Stable feature names need
  neither marker. An alpha implementation artifact does not make its user-facing settings
  experimental.
- Stable configuration names remain stable even when alpha code reads them. Retain a deprecated
  stable name until the next major version. Experimental and preview names may be removed in a
  subsequent minor release. The `otel.javaagent.testing.*` namespace has no stability guarantee.
- Preserve already-published declarative names rather than renaming them to match a mechanical
  conversion. Determine whether normalized flat-property lookup already resolves the name before
  adding a `SPECIAL_MAPPINGS` bridge entry.

## Reading settings

Instrumentation code uses the declarative API for both flat properties and native YAML.
`ConfigPropertiesBackedDeclarativeConfigProperties` exposes flat values through that API.

- In javaagent instrumentation, read ordinary settings through `DeclarativeConfigUtil`, supplying
  a default for unavailable YAML. Nullable reads are intentional when probing for replacement or
  deprecated names before choosing a default.
- Reserve direct flat `ConfigProperties` reads in javaagent configuration for enablement
  bootstrapping in `AgentDistributionConfig`. Outside javaagent instrumentation, direct reads
  remain valid when required by an SDK SPI or bridge contract.
- Read module settings under `java.<module>`, shared instrumentation settings under `java.common`,
  and general settings under `general`. HTTP header capture is general configuration.
- A declarative `ComponentProvider.getName()` matches its YAML node and uses snake_case.
- Structured lists, YAML-only settings, and mode-specific initialization paths need declarative-mode
  coverage. Equivalent flat/YAML behavior using shared logic does not need duplicate module tests.

Prefer default-behavior and module-specific flat-property tests for ordinary settings available
in both forms. Keep declarative coverage focused on distinct binding, parsing, initialization,
migration paths, structured settings, and native configuration integration. Single-source
declarative unit tests are not duplicates.

Test shared conversion, defaults, fallback, and precedence centrally. Keep caller-specific logic
covered and add missing central coverage before removing module tests. Remove unused fixtures,
dependencies, task registrations, and metadata-collection inputs with redundant variants, but
retain metadata describing supported settings.

## Renaming ordinary settings

Read the replacement first, fall back to the deprecated name only when it is absent, then choose
the default. Log a `WARN` once at startup when the deprecated value is actually applied. Name the
old and replacement flat properties, or their declarative paths if there is no flat form.
Deduplicate repeated reads with an existing once-only mechanism, such as a static `AtomicBoolean`
for one setting or a keyed set for several, unless initialization guarantees one evaluation.

```java
Boolean value = config.getBoolean("new_key");
if (value != null) {
  return value;
}
Boolean deprecatedValue = config.getBoolean("old_key");
if (deprecatedValue != null) {
  warnOnce();
  return deprecatedValue;
}
return defaultValue;
```

Carrying both names must not warn when the replacement determines the result. This lets operators
roll out a shared configuration to old and new deployments without warning on upgraded deployments.
Document the deprecation in the CHANGELOG, including instrumentation enablement alias renames.
Keep a code comment identifying the deprecated read.

Instrumentation enablement aliases are different: enablement resolves an ordered list of equivalent
names, while warnings are driven by explicit legacy-key presence. A replacement alias may determine
the result while the legacy alias still warns. Do not use that warning behavior for ordinary
replacement/fallback settings.

## Structured selectors

Use noun-based names for structured `included`/`excluded` selectors, such as `mdc_attributes`,
`headers`, or `request_parameters`. A selector describes the selected resource, not a capture action.
Keep `capture_*` for action booleans such as `capture_query` or `capture_message_content`.
Java setters likewise use names such as `setMdcAttributes(IncludeExclude)`, not
`setCaptureMdcAttributes(...)`. Prefer one selector value to paired include/exclude setters; the
shared `IncludeExclude` type is the intended shape.

```yaml
mdc_attributes:
  included: [trace_id, request_id]
  excluded: [password]
```

The setting's baseline determines what absence means:

| Baseline | Absent or empty selector | Omitted `included` with nonempty `excluded` |
| --- | --- | --- |
| None by default | Select nothing | Select everything except excluded values |
| All by default | Keep everything | Keep everything except excluded values |

An empty selector has no patterns in either list. Treat it as absent and use the setting's own
default; `IncludeExclude.isEmpty()` identifies it. This matches flat configuration, which cannot
distinguish empty values from unset values.

For a none-by-default setting, document select-all as `included: ["*"]`, or `included=*` in flat
configuration. Flat configuration cannot express a present selector with no included patterns.

Document pattern syntax, case sensitivity, precedence, and absence behavior in the setting's
property table, metadata description, and public setter Javadoc. Describe precedence directly:
"Excluded patterns take precedence over included patterns." Do not describe it as an order of
application that might imply later rules can re-add excluded values.
