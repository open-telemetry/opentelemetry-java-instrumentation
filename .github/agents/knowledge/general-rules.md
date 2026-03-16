# General Rules (Always Enforce)

## Quick Reference

- Use when: always — load this file for every review
- Review focus: engineering correctness, style, naming, semconv, config, testing, new modules

## Review Checklist

Use category tags like `[Style]`, `[Naming]`, `[Javaagent]`, `[Testing]`.

When a "Knowledge File" is listed, load it from `knowledge/` before reviewing that category.

| Category | Rule | Scope Trigger | Knowledge File |
| --- | --- | --- | --- |
| General | Logic, correctness, reliability, safety, copy/paste mistakes, incorrect comments | Always | — |
| Style | Style guide | Always | — |
| Naming | Getter naming (`get` / `is`) | Always | — |
| Naming | Module/package naming | New or renamed modules/packages | `module-naming.md` |
| Javaagent | Advice patterns | `@Advice` classes | `javaagent-advice-patterns.md` |
| Javaagent | Module structure patterns | `InstrumentationModule`, `TypeInstrumentation`, `Singletons` | `javaagent-module-patterns.md` |
| Javaagent | Incorrect `classLoaderMatcher()` | `classLoaderMatcher()` override that is redundant (muzzle already handles it) or missing when needed (muzzle cannot distinguish version range) | `javaagent-module-patterns.md` |
| Semconv | Library vs javaagent semconv constant usage | Semconv constants/assertions | — |
| Semconv | Dual semconv testing | `SemconvStability`, `maybeStable`, semconv Gradle tasks | `testing-semconv-stability.md` |
| Testing | General test patterns | Test files in scope | `testing-general-patterns.md` |
| Testing | Experimental flag tests | `testExperimental`, experimental attribute assertions, `experimental` flags in JVM args or system properties | `testing-experimental-flags.md` |
| Library | TelemetryBuilder/getter/setter patterns | Library instrumentation classes | `library-patterns.md` |
| API | Deprecation and breaking-change policy | Public API changes | `api-deprecation-policy.md` |
| Config | Config property stability/renames/removals | `otel.instrumentation.*` property changes, `DeclarativeConfigUtil` or `ConfigProperties` usage | `config-property-stability.md` |
| Build | Gradle conventions, muzzle, test tasks, plugins | `build.gradle.kts`, `settings.gradle.kts` | `gradle-conventions.md` |
| Build | `testcontainersBuildService` declaration | Testcontainers dependency without `usesService` | `gradle-conventions.md` |
| Style | Prefer instance creation over singletons for stateless interface impls | `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`, `AttributesExtractor`, `SpanNameExtractor`, `HttpServerResponseMutator`, enum/static singletons | — |
| Style | Remove redundant null guards on attribute puts | `AttributesBuilder.put`, `onStart`, `onEnd`, attribute extraction methods | — |
| Style | Nullability correctness — no guards for non-nullable params; add `@Nullable` when null is actually passed/returned; respect upstream SDK `@Nullable` contracts for `TextMapGetter`/`TextMapSetter` | `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`, `*Extractor` implementations, null checks, missing `@Nullable` | — |
| Architecture | Library vs javaagent boundaries | Always | — |
| NewModule | New instrumentation module checklist | New modules | _(inline below)_ |

## [General] Engineering Correctness

Flag real defects, including:

- logic errors
- concurrency hazards
- resource leaks
- copy/paste mistakes
- incorrect comments
- unsafe error handling
- high-risk edge cases
- dead code
- security regressions

Only flag substantive problems, not stylistic preference.

## [Style] Style Guide

Read and apply `docs/contributing/style-guide.md`.

Do not flag the following patterns (common false positives):

- FQCN is acceptable when class-name collision makes import impossible.

## [Style] `@SuppressWarnings` Usage

- Method-level `@SuppressWarnings` is preferred over class-level for tighter scope, but
  if more than one method in the class needs the same suppression, class-level is fine.
  Do not flag class-level `@SuppressWarnings` when multiple methods use the suppressed API.
- **Do not add `@SuppressWarnings("deprecation")` unless the build fails without it.**
  The project disables javac's `-Xlint:deprecation` globally and uses a custom Error Prone
  check (`OtelDeprecatedApiUsage`) instead. Only add the annotation when it is actually
  required to fix an Error Prone error — not speculatively.

## [Naming] Getter Naming

Public API getters should use `get*` (or `is*` for booleans).

## [Style] Prefer Instance Creation Over Singletons

Stateless implementations of telemetry interfaces — `TextMapGetter`, `TextMapSetter`,
`*AttributesGetter`, `AttributesExtractor`, `SpanNameExtractor`,
`HttpServerResponseMutator` — should use instance creation (`new MyGetter()`) instead of
singleton patterns.

Replace every singleton reference (`MyGetter.INSTANCE`, `MyGetter.getInstance()`) with
`new MyGetter()`. Do not restructure surrounding code — if the original used a local
variable (`MyGetter g = MyGetter.INSTANCE`), keep the variable and only change the
right-hand side to `new MyGetter()`.

Convert the class declaration from `enum` / singleton-holder to a plain `class`.
If the implementation is a private nested class, omit the `final` keyword.

## [Style] No Redundant Null Guards on Attribute Puts

All `put` / `setAttribute` methods on `AttributesBuilder`, `Span`, `SpanBuilder`, and
`LogRecordBuilder` are no-ops when the value is `null` (upstream SDK guarantee).
Do not wrap these calls in `if (value != null)` guards — pass the value directly.

**Exception — primitive-typed attribute keys**: when the `AttributeKey` is typed as
`Long`, `Double`, or `Boolean`, the `put` overload accepts a **primitive** parameter
(`long`, `double`, `boolean`). If the source value is a boxed type (`Integer`, `Long`,
`Double`, `Boolean`) that may be `null`, Java auto-unboxes it **before** `put()` is
reached, causing a `NullPointerException`. In this case the null guard is **required** —
do not remove it.

Flag patterns like:

```java
String v = getSomething();
if (v != null) {
  attributes.put(SOME_KEY, v);
}
```

Preferred:

```java
attributes.put(SOME_KEY, getSomething());
```

Do **not** flag (the guard is required):

```java
Integer statusCode = response.getStatusCode(); // may return null
if (statusCode != null) {
  attributes.put(HTTP_RESPONSE_STATUS_CODE, statusCode); // AttributeKey<Long> → put(long)
}
```

## [Style] Nullability Correctness

Use `@Nullable` annotations accurately throughout the codebase:

- **Parameters**: annotate `@Nullable` if and only if `null` is actually passed by callers.
- **Return types**: annotate `@Nullable` if and only if the method actually returns `null`.
  Even when an interface or superclass declares a return type as `@Nullable`, do **not** add
  `@Nullable` to the overriding method if the implementation never returns `null`. The
  interface annotation permits null, but an implementation that always returns a non-null
  value is more precise without it.
  When justifying `@Nullable` on a return type, cite the concrete reason the implementation
  can return null (e.g., it delegates to a `@Nullable`-returning method without adding a
  non-null guarantee), not merely that an interface or upstream contract permits null.
- **Test files**: do **not** add `@Nullable` in test code.
  If a PR adds `@Nullable` to test files, flag it for removal.
- **External interface contracts**: interfaces from the OpenTelemetry SDK
  (`io.opentelemetry.context.propagation`) declare `@Nullable` on certain parameters.
  These annotations are not visible in this repository because the interfaces live in the
  upstream `opentelemetry-java` SDK. Implementations **must** propagate the upstream
  `@Nullable` annotation to overriding parameter declarations and include appropriate
  null checks. If an implementation is missing `@Nullable` on the parameter or is missing
  a null check for a parameter that is `@Nullable` in the upstream interface, add both
  the annotation and the null guard.

  Upstream nullability contracts:

  | Interface | Method | `@Nullable` Parameters |
  | --- | --- | --- |
  | `TextMapGetter<CarrierT>` | `get(CarrierT, String)` | `carrier` is `@Nullable` |
  | `TextMapGetter<CarrierT>` | `getAll(CarrierT, String)` | `carrier` is `@Nullable` |
  | `TextMapGetter<CarrierT>` | `keys(CarrierT)` | none |
  | `TextMapSetter<CarrierT>` | `set(CarrierT, String, String)` | `carrier` is `@Nullable` |

## [Semconv] Constants by Module Type

- `library/src/main/`: incubating semconv constants (from
  `io.opentelemetry.semconv.incubating`) must be copied locally as `private static final`
  fields with a `// copied from <ClassName>` comment. Stable semconv constants (from
  `io.opentelemetry.semconv`) may be imported directly.
- `javaagent/src/main/`: all semconv artifact constants (stable and incubating) may be used
  directly.
- tests: all semconv artifact constants are allowed.

## [NewModule] New Instrumentation Checklist

If a new module is added, verify all of the following:

1. `metadata.yaml` includes required fields and config metadata.
2. `build.gradle.kts` custom test tasks follow `gradle-conventions.md`.
3. `testExperimental` task exists when module emits experimental span attributes.
4. `instrumentation-docs/instrumentations.sh` includes all test-task variants.
5. `settings.gradle.kts` includes all subprojects in alphabetical order.
6. `docs/supported-libraries.md` has an entry (`Auto-instrumented versions` is `N/A` for
   library-only).
7. Javaagent README (may be in `javaagent/` or parent directory to share across versions)
   documents configuration properties (if any) in a settings table.
8. Library README exists with dependency and usage details (when there is a standalone
   library instrumentation).
9. `.fossa.yml` regenerated via `./gradlew generateFossaConfiguration`.
10. Muzzle `pass` blocks include `assertInverse.set(true)`.
11. Correct Gradle plugin is applied for each module type.

## [Config] Configuration Reading

`DeclarativeConfigUtil` is the standard way to read instrumentation configuration in javaagent
code. Flat `ConfigProperties` is only used directly in `AgentDistributionConfig` for
instrumentation enable/disable bootstrapping (`otel.instrumentation.<name>.enabled`,
`otel.instrumentation.common.default-enabled`). All other config reads go through the
declarative API. Do not flag `DeclarativeConfigUtil` usage as incorrect.

## [Testing] General Test Patterns

See `testing-general-patterns.md` (loaded when test files are in scope).
