# [Javaagent] Advice Patterns

Consult this article when implementing or changing executable Byte Buddy advice
and its helpers. It explains advice registration, exception handling, and
scope ownership with examples of the relevant runtime behavior.

## Advice Classes as Nested Classes

Advice classes (those containing `@Advice.OnMethodEnter` / `@Advice.OnMethodExit` methods) should
be **static nested classes** inside the instrumentation class, not standalone top-level classes.

```java
// ✅ Correct: nested inside instrumentation class
class MyInstrumentation implements TypeInstrumentation {
  // ...

  @SuppressWarnings("unused")
  public static class MethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(/* ... */) { /* ... */ }
  }
}
```

```java
// ❌ Wrong: separate top-level file
// File: MyAdvice.java
public class MyAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(/* ... */) { /* ... */ }
}
```

Add `@SuppressWarnings("unused")` to advice classes — they are invoked by ByteBuddy, not by
direct Java calls, so IDEs may flag them as unused.

**Always place `@SuppressWarnings("unused")` at the class level, not on individual methods.**
Moving it to method level is not an improvement — the entire class is effectively unused from
the IDE's perspective, and per-method placement is inconsistent with the rest of the codebase.
This advice-specific rule intentionally overrides the generic Java cleanup rule to scope
`@SuppressWarnings` as narrowly as possible. Do not "minimize" advice-class `unused`
suppressions to a single advice method.

## Advice Methods Must Be Static

All `@Advice.OnMethodEnter` and `@Advice.OnMethodExit` methods **must be `static`**. ByteBuddy
inlines advice code directly into the instrumented method by default — there is no advice object
instance. Do not add or remove `inline = false` as cleanup; it is a semantic signal used by
muzzle/indy tooling.

```java
// ✅ Correct
@Advice.OnMethodEnter(suppress = Throwable.class)
public static void onEnter(/* ... */) { /* ... */ }

// ❌ Wrong — instance method
@Advice.OnMethodEnter(suppress = Throwable.class)
public void onEnter(/* ... */) { /* ... */ }
```

Advice classes should also have **no instance fields** — they are never instantiated.

## `Java8BytecodeBridge`

When changing advice, inspect both the annotated advice bodies and every helper they
call for `Java8BytecodeBridge` usage.

Use the bridge only for supported OpenTelemetry API calls written directly in
`@Advice.OnMethodEnter` or `@Advice.OnMethodExit` methods, which classic mode may copy into
pre-Java-8 bytecode. Everywhere else, use the direct OpenTelemetry API, for example
`Context.current()` instead of `Java8BytecodeBridge.currentContext()` and
`Span.fromContext(context)` instead of `Java8BytecodeBridge.spanFromContext(context)`.

Helpers called by advice are ordinary compiled methods and are not copied into the instrumented
method, even when the helper is nested in an advice class. Source-level `inline = false` does not
create an exception; the transformer controls inlining.

## Use `suppress = Throwable.class` by Default

Before applying this rule, trace how the advice class is registered in `transform()`. Advice
registered with a `none()` method matcher is a helper-injection-only pattern and never runs; see
the exception below.

Both `@Advice.OnMethodEnter` and `@Advice.OnMethodExit` should include
`suppress = Throwable.class`:

```java
@Advice.OnMethodEnter(suppress = Throwable.class)
public static void onEnter(/* ... */) { /* ... */ }

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(/* ... */) { /* ... */ }
```

This prevents instrumentation failures from crashing the application. Without `suppress`,
any exception thrown inside the advice code propagates to the instrumented method and breaks
the application.

### When to omit `onThrowable` on `@Advice.OnMethodExit`

`onThrowable = Throwable.class` makes the exit advice run even when the instrumented method
throws. This is correct for **cleanup advice** (e.g., closing a `Scope` or ending a span
passed via `@Advice.Enter`), but **wrong for return-only advice** that only processes
`@Advice.Return`.

When the instrumented method throws, `@Advice.Return`-annotated parameters receive their
type's default value (`null` for objects, `0` for primitives). If the advice body
dereferences the return value or passes it to a method that does not tolerate `null`,
it will throw — suppressed by `suppress`, but generating unnecessary noise.

Rule of thumb:

- **Include `onThrowable`** when the exit advice performs cleanup that must happen
  regardless of whether the method succeeded (e.g., closing `Scope`, ending spans).
- **Omit `onThrowable`** when the exit advice only processes the return value
  (`@Advice.Return`) and has nothing to clean up on the exceptional path.

```java
// ✅ Cleanup advice — needs onThrowable so Scope is always closed
@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(@Advice.Enter @Nullable Scope scope) {
  if (scope != null) {
    scope.close();
  }
}

// ✅ Return-only advice — omit onThrowable; nothing to clean up on exception
@Advice.OnMethodExit(suppress = Throwable.class)
public static CompletableFuture<?> onExit(@Advice.Return CompletableFuture<?> result) {
  return CompletableFutureWrapper.wrap(result, currentContext());
}
```

Keep `suppress = Throwable.class` in both cases — it is always required.

### Exceptions — when omitting `suppress` is acceptable

- **Internal infrastructure** (`instrumentation/internal/`): Instrumentations for internal
  class loading and lambda support may intentionally let errors propagate because silently
  swallowing them would corrupt JVM internals.
- **Test code** (`testing-common/`, test sources): Not production instrumentation — suppress
  is not required.

These exceptions do not need `suppress`. For methods with non-trivial bodies
(library calls, collection iteration, reflection), retain it.

### When omitting `suppress` is also acceptable — provably throw-free bodies

For advice methods whose bodies **provably cannot throw** (returning a literal value, reading
a constant, or a single field access with no possibility of NPE), `suppress = Throwable.class`
adds no value and should be omitted rather than added:

```java
// ✅ Omit suppress — return literal, cannot throw
@AssignReturned.ToReturned
@Advice.OnMethodExit
public static boolean methodExit() {
  return true;
}

// ✅ Keep suppress — calls into library code that may throw
@AssignReturned.ToReturned
@Advice.OnMethodExit(suppress = Throwable.class)
public static OpenTelemetry methodExit() {
  return application.io.opentelemetry.api.GlobalOpenTelemetry.get();
}
```

Do not add `suppress = Throwable.class` to such trivially safe advice methods.

### Helper-injection-only advice (`none()` selector) — `suppress` is meaningless

Some instrumentations use a "dummy" advice class solely to force helper class injection.
The `transform()` call uses `none()` as the method matcher, so the advice **never runs**. Check
for this registration pattern before adding `suppress = Throwable.class`:

```java
@Override
public void transform(TypeTransformer transformer) {
  transformer.applyAdviceToMethod(
      none(), getClass().getName() + "$InitAdvice");
}

@SuppressWarnings({"ReturnValueIgnored", "unused"})
public static class InitAdvice {
  @Advice.OnMethodEnter   // no suppress needed — this code is never invoked
  public static void init() {
    // ensures helper class is recognized and injected into classloader
    SomeHelperClass.class.getName();
  }
}
```

Because `none()` matches no methods, ByteBuddy never inlines this advice into anything.
`suppress = Throwable.class` on such a method is meaningless. Remove it if
present, and leave it out even if the advice body contains a helper call.

## AdviceScope Patterns

### Make scope ownership visible

Scope acquisition and closure must make ownership visually obvious. In ordinary code, call
`makeCurrent()` at the call site and use try-with-resources:

```java
try (Scope ignored = context.makeCurrent()) {
  doWork();
}
```

When method advice passes a raw `Scope` through `@Advice.Enter`, call `makeCurrent()` directly in
`@Advice.OnMethodEnter` and close that same returned scope in the paired
`@Advice.OnMethodExit`:

```java
@Advice.OnMethodEnter(suppress = Throwable.class)
public static @Nullable Scope onEnter(Request request) {
  Context context = startContext(request);
  if (context == null) {
    return null;
  }
  return context.makeCurrent();
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(@Advice.Enter @Nullable Scope scope) {
  if (scope != null) {
    scope.close();
  }
}
```

Do not hide `makeCurrent()` in a general helper that returns an open raw `Scope` for its caller to
close. That pattern obscures ownership and makes leaks easy. A helper may instead return a
`Context`, target, or other state, leaving the enter advice to call `makeCurrent()`.

Instrumentation in this repository assumes OpenTelemetry `Context.makeCurrent()` does not throw,
just as it assumes `Scope.close()` does not throw. Do not add a defensive `catch` or
`try`/`finally` solely for a hypothetical failure from `makeCurrent()`. This is a coding
assumption, not a guarantee about arbitrary `ContextStorage` implementations.

Call `makeCurrent()` last, immediately before returning from suppressed enter advice. Complete
fallible setup beforehand so a later failure cannot strand a scope that exit advice never receives.
If independent fallible work must run after acquisition, close the acquired scope if that work
fails. A dedicated `AdviceScope` remains valid when it clearly owns both acquisition and closure
through the established `start()` / `end()` pattern below.

`AdviceScope` usage in this repository falls into **two justified state patterns**.
Use these patterns for new advice instead of treating every existing variation as equally
canonical.

### Close `Scope` before fallible completion work

Method-exit advice and `AdviceScope` completion should close an entered `Scope` at the first safe
point, before other fallible completion or cleanup work. Otherwise, a later failure suppressed by
the advice can leave the context attached to the thread. Closing early also keeps the scope
lifetime as short as possible.

Close the scope directly before fallible work rather than deferring it to a `finally` block.
Closing it in `finally` also avoids a leak, but unnecessarily keeps the context current during the
preceding work. Defer the close only when that exit work intentionally requires the context to
remain current.

Instrumentation in this repository assumes OpenTelemetry `Scope.close()` does not throw. If another
cleanup action follows it, close the scope and then perform that action directly; do not wrap the
close in `try`/`finally` solely to guard against a hypothetical failure from `Scope.close()`. Use
`try`/`finally` when earlier fallible work must complete before the scope can be closed.

### Pattern 1 — Nullable `AdviceScope` for ordinary advice

Use this by default when enter advice may decide not to start instrumentation.

- `@Advice.OnMethodEnter` returns `@Nullable AdviceScope`
- The factory method returns `null` when preconditions fail or instrumentation should not start
- If an `AdviceScope` is created, its `Context` and `Scope` fields should be non-null
- The `end()` method should close `scope` unconditionally; the null check belongs in exit advice,
  not inside `AdviceScope.end()`

In this document, use `start()` / `end()` as the canonical `AdviceScope` naming.
For new code, prefer `start()` for the factory method and `end()` for the completion method.
Do not introduce one-off names such as `create()` for ordinary `AdviceScope` factories.

Preferred shape:

```java
public static class AdviceScope {
  private final Context context;
  private final Scope scope;

  private AdviceScope(Context context) {
    this.context = context;
    this.scope = context.makeCurrent();
  }

  @Nullable
  public static AdviceScope start(Request request) {
    Context parentContext = Context.current();
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    Context context = instrumenter().start(parentContext, request);
    return new AdviceScope(context);
  }

  public void end(@Nullable Throwable throwable) {
    scope.close();
    instrumenter().end(context, request, null, throwable);
  }
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(
    @Advice.Thrown @Nullable Throwable throwable,
    @Advice.Enter @Nullable AdviceScope adviceScope) {
  if (adviceScope != null) {
    adviceScope.end(throwable);
  }
}
```

### Pattern 2 — Non-null placeholder `AdviceScope` for bookkeeping

Use a non-null `AdviceScope` with nullable internals only when exit advice still needs state even
if no tracing scope was started, for example:

- `CallDepth` tracking
- wrapped arguments or listeners that must be carried from enter to exit
- other bookkeeping that must survive even on the "no span started" path

In this pattern, `AdviceScope` is the carrier object for control-flow state, not just tracing
state. Nullable inner fields and exit guards are justified here.

### Do not use the defensive hybrid as a standard pattern

Avoid the hybrid shape where:

- `@Advice.Enter` is `@Nullable AdviceScope`
- `AdviceScope` still stores a nullable `Scope`
- `AdviceScope.end()` has an extra `if (scope == null)` guard

That double-guards the same condition and makes simple advice harder to reason about.
If the helper that creates the context can return `null`, prefer returning `null` from
`AdviceScope.start()` and only creating `AdviceScope` when the context is present.

### Async completion is not a separate `AdviceScope` state pattern

Async instrumentations may end spans from listeners, callbacks, or wrapped handlers instead of
directly in method exit. That affects **where** the scope/span is completed, but it does not
justify a third core `AdviceScope` state model. Async advice should still use either Pattern 1 or
Pattern 2 as appropriate.

## Never Throw Exceptions in Javaagent Code

Javaagent instrumentations must never throw exceptions. The goal is to be invisible to the
application — if the instrumented library changes in an incompatible way, muzzle disables
the instrumentation automatically rather than letting it fail at runtime.

- Do not throw exceptions in advice code.
- Do not throw exceptions in helper classes called from advice.
- Use `suppress = Throwable.class` as the last safety net (see above).
