# [Javaagent] Advice Patterns

## Quick Reference

- Use when: reviewing ByteBuddy advice classes/methods (`@Advice.OnMethodEnter` / `@Advice.OnMethodExit`)
- Review focus: nested advice classes, static advice methods, `suppress = Throwable.class`, no-throw behavior

## Advice Classes as Nested Classes

Advice classes (those containing `@Advice.OnMethodEnter` / `@Advice.OnMethodExit` methods) should
be **static nested classes** inside the instrumentation class, not standalone top-level classes.

```java
// ✅ Correct: nested inside instrumentation class
public class MyInstrumentation implements TypeInstrumentation {
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

## Advice Methods Must Be Static

All `@Advice.OnMethodEnter` and `@Advice.OnMethodExit` methods **must be `static`**. ByteBuddy
inlines advice code directly into the instrumented method — there is no advice object instance.

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
The `transform()` call uses `none()` as the method matcher, so the advice **never runs**:

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
and **do not flag its absence** during review.

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
- **`@Advice.OnMethodEnter` or `@Advice.OnMethodExit` missing `suppress = Throwable.class`** when the method has a non-trivial body (library calls, collection iteration, reflection). Exceptions: `instrumentation/internal/` infrastructure code, test sources, and methods whose bodies provably cannot throw (e.g., `return true;`, returning a literal or a single constant). Do not add or flag `suppress` on provably throw-free bodies.
- **Exception thrown in advice code or a helper called from advice** — javaagent code must never throw; use `suppress = Throwable.class` as the safety net.
- **`@Advice.OnMethodExit` method named `onEnter`** (or vice versa) — the method name should match the annotation. A mismatch is a copy-paste bug that compiles but confuses readers and may mask intent errors.
- **Advice referenced in `transform()` using anything other than `getClass().getName() + "$InnerAdvice"`** — see `javaagent-module-patterns.md` for the canonical pattern. Flag `this.getClass().getName() + "$InnerAdvice"` as a redundant qualifier, and flag both `InnerAdvice.class.getName()` and `OuterInstrumentation.class.getName() + "$InnerAdvice"` because any `.class` literal in a `transform()` method triggers unwanted class loading.
- **`onThrowable = Throwable.class` on return-only exit advice** — if the exit method only processes `@Advice.Return` and has no `@Advice.Enter` state to clean up, `onThrowable` should be omitted. The return value is `null`/zero on the exceptional path, and dereferencing it causes a suppressed exception for no benefit. Keep `suppress = Throwable.class` but remove `onThrowable`.
