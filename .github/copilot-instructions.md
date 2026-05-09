# OpenTelemetry Java Instrumentation

First-pass PR review rules. A deep review with full knowledge files runs
separately later in the PR lifecycle. **Prefer silence over uncertainty.** Only
flag substantive issues on changed lines. Skip stylistic preferences not listed
below. Do not nitpick.

Do not flag anything CI will catch. This includes compilation errors (missing
imports, unbalanced braces, type errors, unresolved symbols), Spotless-covered
formatting (indentation, wrapping, alignment, brace placement, import
ordering/grouping, whitespace), Checkstyle/ErrorProne/NullAway findings, and
test failures. Do not ask authors to run the formatter. CI surfaces these
directly, so review comments on them are noise.

Use category tags like `[Style]`, `[Naming]`, `[Testing]`, `[General]`.

## [Style] Style Guide

Follow `docs/contributing/style-guide.md`.

- **Visibility**: principle of least access. Use the most restrictive modifier
  that still works. Static fields should be `private` unless they are
  constant-like with a `SCREAMING_SNAKE_CASE` name.
- **`final` on classes**: declare public API classes `final` where possible. Do
  **not** add `final` in `javaagent/src/main/`, in `.internal` packages, or in
  test code (paths under `src/test/` or modules whose name starts/ends with
  `testing` or `tests`).
- **`final` on parameters and local variables**: never declare them `final`.
- **Null comparisons**: use `value == null` / `value != null`, not
  `null == value` / `null != value`. Applies to Java, Kotlin, and Scala.
- **`equals` operand order**: prefer `value.equals(CONSTANT)` over
  `CONSTANT.equals(value)`. Do not flip operand order solely as a defensive
  null-safety cleanup; only flip when `value` can actually be null.
- **Class organization**: static fields → static initializer → instance fields
  → constructors → methods → nested classes. Place calling methods above the
  methods they call.
- **Static factory entry points**: place them below fields and immediately
  above constructors — treat factories and constructors as one construction
  section.
- **Static utility classes**: place the private no-arg constructor after all
  methods.
- **Uppercase field names**: use `SCREAMING_SNAKE_CASE` only for constant-like
  values — literals, immutable value constants (e.g. `Duration` timeouts),
  semantic keys/handles (`AttributeKey`, `ContextKey`, `VirtualField`,
  `MethodHandle`, `Pattern`), and canonical singletons (`INSTANCE`, `EMPTY`,
  `NOOP`). Use lower camel case for runtime collaborators (loggers,
  instrumenters, helpers, caches), even when `static final`.
- **Avoid throwaway forwarding locals** that mirror an existing constant,
  argument, or SDK field into both an SDK call and span attributes; pass the
  original value directly unless real derivation justifies a local.
- **`Optional`**: do not use in public API signatures or on the hot path.
- **Semconv constants**: in `library/src/main/`, copy incubating semconv
  constants locally as `private static final` with a `// copied from <Class>`
  comment; do not depend on the semconv incubating artifact. In
  `javaagent/src/main/` and tests, use semconv constants directly.
- **`@Nullable` in tests**: do not add it to test code.

## [Style] `@SuppressWarnings` Scoping

Place `@SuppressWarnings` on the single member that needs it. Use class-level
only when two or more members would need the same suppression.

## [Naming] Catch Variable Names

In **catch clauses only** (not method/lambda parameters or fields):

- Used exception → `e` (or `error` for a specific `*Error` subtype).
- Used exception in nested catch where outer already uses `e` → `f`.
- Used `Throwable` → `t`.
- Intentionally unused → `ignored` (or `ignore` if `ignored` would shadow an
  outer catch).

## [Naming] Public API Getters

Public API getters use `get*` (or `is*` for booleans).

## [Style] No Redundant Null Guards on Attribute Puts

`AttributesBuilder.put`, `Span.setAttribute`, `SpanBuilder.setAttribute`, and
`LogRecordBuilder.setAttribute` are no-ops when the value is `null`. Do not wrap
calls in `if (value != null)` when the value can be passed straight through:

```java
// BAD
String v = getSomething();
if (v != null) {
  attributes.put(SOME_KEY, v);
}
// GOOD
attributes.put(SOME_KEY, getSomething());
```

Do **not** flag when the guard protects a dereference or derived computation
(e.g. `view.getClass().getName()`). When in doubt, stay silent.

## [Testing] General Patterns

- Use AssertJ (`assertThat(...)`) for assertions in new test code. Do not
  use JUnit `Assert.*` or Hamcrest `assertThat`.
- Do not add AssertJ `.as(...)` descriptions or `.withFailMessage(...)` in
  tests. Direct assertions whose failure output already shows the unexpected
  values are preferred.
- Test methods do not need `throws Exception` clauses unless actually required.
- Prefer the nearest common parent in `catch` (including `Exception` /
  `Throwable`) over multi-catch.
- Use `e` / `f` / `t` / `ignored` per the naming rules above.

## [Testing] AssertJ Idiomatic Simplifications

Prefer built-in AssertJ collection/string/map assertions over manual extraction:

| Anti-pattern | Idiomatic |
| --- | --- |
| `assertThat(list.size()).isEqualTo(N)` | `assertThat(list).hasSize(N)` |
| `assertThat(list.isEmpty()).isTrue()` / `.hasSize(0)` | `assertThat(list).isEmpty()` |
| `assertThat(list.contains(x)).isTrue()` | `assertThat(list).contains(x)` |
| per-index `get(i)` checks of every element | `assertThat(list).containsExactly(a, b, ...)` |

`containsExactly` already verifies size, so a separate `hasSize` is redundant.
Same shape applies to `String.length()`, `Map.size()`, and `array.length` →
`assertThat(...).hasSize(N)`.

## [Testing] Span Attribute Assertions

- Prefer `hasAttributesSatisfyingExactly(...)` over `hasAttributesSatisfying(...)`
  — the non-exact variant **silently ignores unexpected attributes**. Also
  prefer it over `hasAttributes(...)` for consistency.
- For zero-attribute span assertions, use `hasTotalAttributeCount(0)`.
- `hasTotalAttributeCount(...)` paired with `hasAttributesSatisfyingExactly(...)`
  is redundant — the exact variant already validates the count. Remove the
  count call.
- Metric points are different: there is no `hasTotalAttributeCount(...)` on
  metric points, so use `point.hasAttributes(Attributes.empty())` for empty
  metric-point checks.
- Do not introduce redundant `(long)` casts in `equalTo(longKey(...), value)`
  when `value` is already an `int` — the `equalTo(AttributeKey<Long>, int)`
  overload exists.

## [Testing] `satisfies()` Lambda Parameters

Inside a `satisfies(AttributeKey, lambda)` attribute-assertion the lambda
parameter is an `AbstractAssert` (e.g. `AbstractStringAssert<?>`), not the raw
value. Fluent calls like `taskId.contains(jobName)` are already proper
assertions — do **not** wrap them in `assertThat(value.contains(x)).isTrue()`,
which degrades the failure message.

Name the outer parameter `val` in Java (or `value` in Scala, where `val` is
reserved). Use `v` only for a nested inner-lambda parameter.

This guidance applies only to attribute-assertion `satisfies(...)`; for
`span.satisfies(...)`, `point.satisfies(...)`, etc. use a descriptive name
(`spanData`, `pointData`, `result`).

## [Javaagent] Singleton Accessor Naming

In `*Singletons`, `*SpanNaming`, and similar holder classes, zero-arg accessor
methods that **directly return a stored singleton field** must match the field
name with no `get` prefix:

```java
private static final Instrumenter<Request, Response> instrumenter = ...;

public static Instrumenter<Request, Response> instrumenter() {
  return instrumenter;
}
```

- Methods that take arguments or compute a value are not singleton accessors —
  keep their normal names (including `get*` when appropriate). Do not flag
  `getAddressAndPort(client)` on this basis.
- Uppercase constant-like fields (e.g. `VirtualField`, `ContextKey`) may be
  exposed as `public static final` directly with no accessor.
- Caller sites should static-import the accessor / constant and call it
  unqualified.

## [General] Engineering Correctness

Flag real defects on changed lines: logic errors, concurrency hazards, resource
leaks, copy/paste mistakes, incorrect comments, unsafe error handling, dead
code, security regressions. Skip stylistic preferences not listed above.
