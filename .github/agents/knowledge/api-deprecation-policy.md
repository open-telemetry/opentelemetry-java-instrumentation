# [API] Breaking Changes and Deprecation Policy

Use this article when changing a published API or renaming a module. It
explains how artifact stability sets removal timing and how to preserve
configuration aliases and telemetry identity during migration.

## What Counts as "Public API"

"API" here means **anything a user's code or configuration depends on by name**, including:

- Java symbols in published artifacts (classes, methods, fields in `:library`, `:testing`,
  `instrumentation-api*`).
- Non-private `Experimental*` helpers in published artifacts, even under `.internal` packages.
- User-facing configuration keys — `otel.instrumentation.<name>.enabled`, any
  `otel.instrumentation.*` property, and the equivalent declarative YAML keys.
- Outgoing telemetry identity — anything users can match on in their backend, including
  `otel.scope.name`, span names, metric names, attribute keys, and attribute values. Users
  build dashboards, filters, and alerts on these values, so silently renaming them is a
  breaking change.

A rename of any of these surfaces is a breaking change even if no Java symbol moved.

### Replacement stability

A replacement must be at least as stable as the contract being deprecated. Do not direct users of
a stable API to an incubating or experimental API.

## When Are Breaking Changes Allowed?

Only in **non-stable (alpha) modules** — i.e. artifacts whose version has the `-alpha` suffix.
Stable module APIs follow strict backwards compatibility:

- Items in stable modules **can be deprecated** in any minor release.
- Deprecated items in stable modules **cannot be removed until the next major version** (currently targeting 3.0).

The CHANGELOG uses distinct headings to distinguish:

- `⚠️ Breaking changes to non-stable APIs` — alpha/non-stable modules (routine)
- `⚠️ Breaking Changes` — stable module changes (rare, requires strong justification)

### Determining whether a symbol is stable

Removal timing follows the stability of the **artifact that publishes the symbol**, not how public
the class looks. A module is stable only when its own `gradle.properties` sets `otel.stable=true`,
equivalently when its published version has no `-alpha` suffix. Check the module directory rather
than a memorized list of stable artifacts, which goes out of date.

Classes that look like supported public API are frequently alpha: `*TelemetryBuilder` and
`internal.Experimental` classes in `instrumentation/**/library` modules, and everything in
`instrumentation-api-incubator`, are published only from `-alpha` artifacts.

### Javaagent modules are not a public API

Javaagent modules (Gradle path ends with `:javaagent`, including shared `-common` javaagent
modules) are bundled into the agent jar and are **not** published for external consumption.
Do **not** apply a deprecation cycle to symbols in javaagent modules — rename or change the
API directly and update all in-repo callers in the same commit. The deprecate-then-remove
cycle described below applies only to non-stable modules whose artifacts are published
(e.g., `:library`, `:testing`, `instrumentation-api*`).

## The Deprecate-Then-Remove Cycle

### Alpha (non-stable) modules

Deprecations in alpha modules are introduced in one monthly release and removed in a subsequent
one. The gap is typically **one release** (approximately one month). Do not schedule an alpha-only
deprecation for 3.0 — it does not have to wait for a major version.

### Stable modules

Deprecations in stable modules accumulate over multiple releases and are only **removed in the
next major version** (3.0). Many items carry `// to be removed in 3.0` or `@deprecated ... Will
be removed in 3.0` comments to make this explicit.

### 3.0 milestone work is separate

Some deprecations are scheduled for 3.0 because 3.0 is where a *behavior* changes, not because the
symbol is stable. These correctly say 3.0 even in alpha modules, so leave them alone: old-semconv
support and the `SemconvStability` clusters, `@Override`s of interface methods that are themselves
scheduled for 3.0, anything gated on `otel.instrumentation.common.v3-preview`,
`|deprecated:<old>` instrumentation-name aliases, the Zipkin exporter removal, and the flat
`ConfigProperties` bridge.

## Correct `@Deprecated` Usage

For a symbol published from an alpha artifact:

```java
/**
 * @deprecated Use {@link #newMethod()} instead. May be removed in the next minor release.
 */
@Deprecated // may be removed in the next minor release
public ReturnType oldMethod() {
  return newMethod();  // delegate to the replacement
}
```

Rules:

- Use plain `@Deprecated` — do **not** use `forRemoval=true` or `since="..."` (must stay Java 8 compatible).
- Always include a `@deprecated` Javadoc tag that names the replacement and states the removal timeline.
- An inline comment stating the timeline is strongly encouraged: `// may be removed in the next
  minor release` for alpha symbols, `// to be removed in 3.0` for stable ones. Reuse the same
  sentence in `metadata.yaml` descriptions, README config tables, and any runtime warning, so one
  deprecation reads consistently everywhere.
- Do **not** repeat the removal timeline in the CHANGELOG bullet — it says what is deprecated and
  what replaces it. The timeline lives where a user actually encounters it, and alpha and stable
  surfaces deprecated by the same PR often differ.
- The **deprecated method must delegate to its replacement**, not the other way around. This ensures
  anyone overriding the deprecated method still gets called.
- Add the `deprecation` label to the PR — this drives the automated `🚫 Deprecations` CHANGELOG entry.

### Deprecating default interface methods

The same pattern applies; just add `default` to keep the method callable during the transition:

```java
/**
 * @deprecated Use {@link #configure(IgnoredTypesBuilder)} instead.
 */
@Deprecated
default void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
  configure(builder);
}
```

## Module renames: config keys and emitted scope names

A module rename touches **two user-facing API surfaces** that must each be preserved by default
and only change under `otel.instrumentation.common.v3-preview`. The mechanism differs because
the surfaces are different: config keys can coexist as aliases, emitted scope names cannot.

### 1. `InstrumentationModule` names (controls `otel.instrumentation.<name>.enabled`)

The names passed to the `InstrumentationModule` constructor drive the
`otel.instrumentation.<name>.enabled` config keys — any of them, not just the first. A rename
silently breaks users who have the old key in their config.

Keep the pre-rename name by passing the `"<current>|deprecated:<old>"` marker through
`expandDeprecatedNames`:

```java
public CxfInstrumentationModule() {
  super(
      "cxf",
      expandDeprecatedNames("jaxws-2.0-cxf-3.0|deprecated:jaxws-cxf-3.0", "jaxws"));
}
```

The helper splits the marker and registers both names, so both
`otel.instrumentation.jaxws-2.0-cxf-3.0.enabled` and
`otel.instrumentation.jaxws-cxf-3.0.enabled` keep working (flat properties and YAML alike).
Under `otel.instrumentation.common.v3-preview=true` the deprecated name is dropped and its key
silently ignored, matching 3.0; releases before then still warn outside preview mode. Unlike
ordinary replacement-property fallback, the alias warning is driven by explicit legacy-key presence,
so it fires even when the current name determines the effective enablement.

No per-module `AgentCommonConfig` branching, `isV3Preview()` checks, or bespoke logging are
needed — the marker string plus the statically imported `expandDeprecatedNames` call is the
entire change.

### 2. Emitted instrumentation scope name (`INSTRUMENTATION_NAME` in `*Singletons`)

The `INSTRUMENTATION_NAME` string passed to `Instrumenter.builder(...)` becomes the
`otel.scope.name` attribute on every emitted span / metric / log. A rename silently breaks
dashboards and filters that match on the old scope.

Keep emitting the **pre-rename** scope name by default, and switch to the new one only under
v3-preview:

```java
public class CxfSingletons {
  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview()
          ? "io.opentelemetry.jaxws-2.0-cxf-3.0"
          // keep the pre-rename scope name so existing dashboards/filters on
          // otel.scope.name="io.opentelemetry.jaxws-cxf-3.0" continue to work
          : "io.opentelemetry.jaxws-cxf-3.0";
  ...
}
```

Note the asymmetry with the config-key case: there the list carries **both** names at once
(aliases); here only **one** scope name is emitted at a time, and the default is the **old**
one. Do not log a deprecation warning here — users cannot switch the scope name without
enabling v3-preview globally (which affects every module), so a warning would push them
toward something they are not expected to enable generally.

Emitting the old scope name by default has one consequence: the version `.properties` file
generated by `generateInstrumentationVersionFile` is named after the **new** module name
(e.g. `io.opentelemetry.jaxws-2.0-cxf-3.0.properties`), and `Instrumenter.builder` looks the
version up via `EmbeddedInstrumentationProperties.findVersion(instrumentationName)` keyed on
the scope name passed in. With the old name as the default the lookup returns `null`, the
emitted scope has no version, and `TelemetryDataUtil.assertScopeVersion` fails every test in
CI with:

> Instrumentation version of module `io.opentelemetry.<old>` was empty; make sure that the
> instrumentation name matches the gradle module name

Resolve this by looking up the version under the **new** module name and calling
`setInstrumentationVersion` on the builder explicitly:

```java
private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.jaxws-2.0-cxf-3.0";

private static final String INSTRUMENTATION_NAME =
    AgentCommonConfig.get().isV3Preview() ? VERSION_LOOKUP_NAME : "io.opentelemetry.jaxws-cxf-3.0";

static {
  InstrumenterBuilder<CxfRequest, Void> builder =
      Instrumenter.<CxfRequest, Void>builder(
              GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, CxfRequest::spanName)
          .setEnabled(...);
  String version = EmbeddedInstrumentationProperties.findVersion(VERSION_LOOKUP_NAME);
  if (version != null) {
    builder.setInstrumentationVersion(version);
  }
  instrumenter = builder.buildInstrumenter();
}
```

### CHANGELOG

An instrumentation-name alias rename belongs under `🚫 Deprecations`, not breaking changes, while
the compatibility alias remains. Record the breaking removal when v3-preview behavior becomes the
default in 3.0.
