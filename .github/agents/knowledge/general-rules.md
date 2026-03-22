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
| Style | Prefer instance creation over singletons for stateless interface impls (except on hot paths or Kotlin `object` declarations) | `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`, `AttributesExtractor`, `SpanNameExtractor`, `HttpServerResponseMutator`, enum/static singletons | — |
| Style | Remove redundant null guards on attribute puts | `AttributesBuilder.put`, `onStart`, `onEnd`, attribute extraction methods | — |
| General | No redundant `ByteBuffer.duplicate()` on `Value.getValue()` | `Value.getValue()` with `BYTES` type, `ByteBuffer` handling | — |
| Style | Nullability correctness — no guards for non-nullable params; add `@Nullable` when null is actually passed/returned/stored; respect upstream SDK `@Nullable` contracts for `TextMapGetter`/`TextMapSetter` | `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`, `*Extractor` implementations, null checks, missing `@Nullable`, fields assigned from `@Nullable` sources | — |
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

**Exception — Kotlin `object` declarations**: Kotlin `object` is an idiomatic
language-level singleton. Do not convert `object` declarations to `class`. This
rule targets Java `enum` singletons and static `INSTANCE` fields only.

**Exception — hot paths**: when the getter/setter is used in a per-request or
per-message code path (e.g., inside `propagator.extract()` or `propagator.inject()`
called at request time), keep the singleton instance (`INSTANCE` field) to avoid
allocating on every invocation. The instance-creation style is intended for
registration-time call sites such as `Instrumenter` builder chains and `Singletons`
initialization — not for code that runs on every request.

## [Style] No Redundant Null Guards on Attribute Puts

All `put` / `setAttribute` methods on `AttributesBuilder`, `Span`, `SpanBuilder`, and
`LogRecordBuilder` are no-ops when the value is `null` (upstream SDK guarantee).
Do not wrap these calls in `if (value != null)` guards — pass the value directly.

**Exception — `AttributeKey<Long>` with `Integer` value**: the only primitive-typed
overload on these interfaces is a convenience method that accepts `int`:

- `AttributesBuilder.put(AttributeKey<Long> key, int value)`
- `Span.setAttribute(AttributeKey<Long> key, int value)`

When the `AttributeKey` is typed as `Long` and the source value is `Integer`, Java
cannot bind `Integer` to `T = Long` in the generic overload (type mismatch), so it
resolves to the `int` convenience overload via auto-unboxing (`Integer` → `int`). If
the `Integer` is `null`, this auto-unboxing causes a `NullPointerException` **before**
`put()` / `setAttribute()` is reached. In this case the null guard is **required** — do
not remove it.

When the value type **matches** the `AttributeKey` type parameter (e.g.,
`Boolean` → `AttributeKey<Boolean>`, `Long` → `AttributeKey<Long>`,
`Double` → `AttributeKey<Double>`), the generic `@Nullable T` overload is selected
directly — no auto-unboxing occurs and `null` is safe. Do **not** add a null guard in
this case.

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

Also flag (the guard is unnecessary — types match, generic overload handles null):

```java
Boolean enabled = metadata.getEnabled();       // may return null
if (enabled != null) {
  span.setAttribute(META_ENABLED, enabled);    // AttributeKey<Boolean> + Boolean → generic overload
}
```

Preferred:

```java
span.setAttribute(META_ENABLED, metadata.getEnabled()); // null is a no-op
```

Do **not** flag (the guard is required — type mismatch forces `int` overload):

```java
Integer statusCode = response.getStatusCode(); // may return null
if (statusCode != null) {
  attributes.put(HTTP_RESPONSE_STATUS_CODE, statusCode); // AttributeKey<Long> + Integer → put(int)
}
```

## [Style] Nullability Correctness

Use `@Nullable` annotations accurately throughout the codebase:

- **Fields**: annotate `@Nullable` if and only if the field can hold `null` at any point
  after construction (e.g., it is assigned from a `@Nullable` method, set to `null`
  explicitly, or left uninitialized). If the field is always non-null after the
  constructor completes, do not annotate it.
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

  **Exception — pure delegation**: when the entire body of the overriding method is a
  single delegation to another `TextMapGetter` or `TextMapSetter` instance (i.e., the
  implementation contains no carrier-specific logic and simply calls
  `delegate.get(carrier, key)`, `delegate.getAll(carrier, key)`, or
  `delegate.set(carrier, key, value)`), do **not** add a null guard for `carrier`.
  The delegate is itself a `TextMapGetter`/`TextMapSetter` and must handle `null` carrier
  per the same contract. Just annotate the parameter with `@Nullable` and pass
  `carrier` straight through:

  ```java
  // CORRECT — pure delegation, no null guard needed
  @Override
  @Nullable
  public String get(@Nullable C carrier, String key) {
    return delegate.get(carrier, key);
  }

  // WRONG — redundant null guard before delegation
  @Override
  @Nullable
  public String get(@Nullable C carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return delegate.get(carrier, key);
  }
  ```

## [General] No Redundant `ByteBuffer.duplicate()` on `Value.getValue()`

The upstream `Value<ByteBuffer>` implementation (`ValueBytes` in `opentelemetry-java`)
returns a **new read-only `ByteBuffer`** on every call to `getValue()`:

```java
// opentelemetry-java ValueBytes.getValue()
return ByteBuffer.wrap(raw).asReadOnlyBuffer();
```

Do not wrap the result in `.duplicate()` — each `getValue()` call already yields a fresh
buffer with independent position/limit state. A `.duplicate()` adds overhead for no safety
benefit.

Flag:

```java
ByteBuffer byteBuffer = ((ByteBuffer) value.getValue()).duplicate();
```

Preferred:

```java
ByteBuffer byteBuffer = (ByteBuffer) value.getValue();
```

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
