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

## [Naming] VirtualField Handle Field Names

A `static final VirtualField` field must use `SCREAMING_SNAKE_CASE`, regardless
of visibility (`private` or `public`) and regardless of the fact that
`VirtualField.find(...)` creates the handle at runtime rather than at compile
time. Flag a camelCase `static final VirtualField` field.

Other semantic key/handle types (`AttributeKey`, `ContextKey`, `MethodHandle`,
`Pattern`) are good candidates for uppercase names too, but this repository
does not yet treat that as mandatory — do not flag existing camelCase
`MethodHandle` or `Pattern` fields on this basis alone.

Runtime collaborator objects (loggers, instrumenters, helpers, caches, and
similar service objects) keep lower camel case even when `static final` — do
not flag those.

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

## [Javaagent] Prefer VirtualField for Per-Object State

When javaagent or shared bootstrap code introduces a weak-key or identity-keyed
registry to attach instrumentation state to third-party object instances, prefer
`VirtualField`. If shared logic cannot name the library type, keep the
`VirtualField` with the caller that knows the concrete carrier and pass the
typed handle or a typed accessor into the shared helper; do not replace
`Cache<Object, State>` with `VirtualField<Object, State>`.

Flag this only when the value is state belonging to that exact carrier and the
carrier type and lifecycle are clear. Do not apply it to real memoization such
as `Class`/`ClassLoader` metadata caches, bounded caches, deliberate weak
callback/delegate links, value-equality interning pools, or non-javaagent
library code. See
`.github/agents/knowledge/javaagent-virtual-fields.md` for the full decision
guide.

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
