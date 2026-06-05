# [Javaagent] Advice Patterns

## Quick Reference

- Use when: reviewing ByteBuddy advice classes/methods (`@Advice.OnMethodEnter` / `@Advice.OnMethodExit`)
- Review focus: nested advice classes, static advice methods, `suppress = Throwable.class`, no-throw behavior

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

When reviewing, **do not flag** these patterns. Focus on advice methods with non-trivial
bodies (library calls, collection iteration, reflection) that are missing `suppress`.

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

**Do not add `suppress = Throwable.class`** when writing or reviewing such trivially-safe advice
methods. Equally, **do not flag the absence of `suppress`** on these methods as a review issue.

### Helper-injection-only advice (`none()` selector) — `suppress` is meaningless

Some instrumentations use a "dummy" advice class solely to force helper class injection.
The `transform()` call uses `none()` as the method matcher, so the advice **never runs**. Check
for this registration pattern before adding or flagging missing `suppress = Throwable.class`:

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
`suppress = Throwable.class` on such a method is entirely meaningless — **do not add it**,
**remove it when found**, and **do not flag its absence** during review, even if the advice body
contains a helper call.

## AdviceScope Patterns

`AdviceScope` usage in this repository falls into **two justified state patterns**.
Review new code against these patterns instead of treating every existing variation as equally
canonical.

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

## What to Flag in Review

- **Advice class is a top-level file** instead of a static nested class inside the `TypeInstrumentation` — move it inside.
- **Advice class missing `@SuppressWarnings("unused")`** — ByteBuddy invokes it reflectively; IDEs will flag it as dead code without the annotation. Always place the annotation **at the class level**, never moved down to individual methods.
- **`@Advice.OnMethodEnter` or `@Advice.OnMethodExit` method is not `static`** — advice methods must be static.
- **Advice class has instance fields** — advice classes are never instantiated; state must not be stored on them.
- **`@Advice.OnMethodEnter` or `@Advice.OnMethodExit` missing `suppress = Throwable.class`** when the method has a non-trivial body (library calls, collection iteration, reflection). Exceptions: helper-injection-only advice registered with `none()`, `instrumentation/internal/` infrastructure code, test sources, and methods whose bodies provably cannot throw (e.g., `return true;`, returning a literal or a single constant). Do not add or flag `suppress` on these exceptions.
- **Exception thrown in advice code or a helper called from advice** — javaagent code must never throw; use `suppress = Throwable.class` as the safety net.
- **`@Advice.OnMethodExit` method named `onEnter`** (or vice versa) — the method name should match the annotation. A mismatch is a copy-paste bug that compiles but confuses readers and may mask intent errors.
- **Advice referenced in `transform()` using anything other than `getClass().getName() + "$InnerAdvice"`** — see `javaagent-module-patterns.md` for the canonical pattern. Flag `this.getClass().getName() + "$InnerAdvice"` as a redundant qualifier, and flag both `InnerAdvice.class.getName()` and `OuterInstrumentation.class.getName() + "$InnerAdvice"` because any `.class` literal in a `transform()` method triggers unwanted class loading.
- **`onThrowable = Throwable.class` on return-only exit advice** — if the exit method only processes `@Advice.Return` and has no `@Advice.Enter` state to clean up, `onThrowable` should be omitted. The return value is `null`/zero on the exceptional path, and dereferencing it causes a suppressed exception for no benefit. Keep `suppress = Throwable.class` but remove `onThrowable`.
- **One-off `AdviceScope` method naming** — for new ordinary advice, prefer `start()` / `end()` for `AdviceScope` methods, and avoid introducing unique names such as `create()`.
- **Defensive hybrid `AdviceScope` in simple advice** — if `@Advice.Enter` is already nullable, flag simple advice that also stores a nullable inner `Scope`/`Context` and re-checks it inside `AdviceScope.end()`. Prefer returning `null` from the factory and keeping the created `AdviceScope` fully initialized.
