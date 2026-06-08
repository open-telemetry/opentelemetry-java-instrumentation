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

Java-specific style and test rules live in path-specific files (loaded in
addition to this one when reviewing Java changes):

- `.github/instructions/java-style.instructions.md`
- `.github/instructions/java-tests.instructions.md`

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
