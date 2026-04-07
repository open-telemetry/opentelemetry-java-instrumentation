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

### Exceptions — when omitting `suppress` is acceptable

- **Internal infrastructure** (`instrumentation/internal/`): Instrumentations for internal
  class loading and lambda support may intentionally let errors propagate because silently
  swallowing them would corrupt JVM internals.
- **Test code** (`testing-common/`, test sources): Not production instrumentation — suppress
  is not required.

When reviewing, **do not flag** these patterns. Focus on advice methods with non-trivial
bodies (library calls, collection iteration, reflection) that are missing `suppress`.

## Never Throw Exceptions in Javaagent Code

Javaagent instrumentations must never throw exceptions. The goal is to be invisible to the
application — if the instrumented library changes in an incompatible way, muzzle disables
the instrumentation automatically rather than letting it fail at runtime.

- Do not throw exceptions in advice code.
- Do not throw exceptions in helper classes called from advice.
- Use `suppress = Throwable.class` as the last safety net (see above).

## What to Flag in Review

- **Advice class is a top-level file** instead of a static nested class inside the `TypeInstrumentation` — move it inside.
- **Advice class missing `@SuppressWarnings("unused")`** — ByteBuddy invokes it reflectively; IDEs will flag it as dead code without the annotation.
- **`@Advice.OnMethodEnter` or `@Advice.OnMethodExit` method is not `static`** — advice methods must be static.
- **Advice class has instance fields** — advice classes are never instantiated; state must not be stored on them.
- **`@Advice.OnMethodEnter` or `@Advice.OnMethodExit` missing `suppress = Throwable.class`** when the method has a non-trivial body (library calls, collection iteration, reflection). Exceptions: `instrumentation/internal/` infrastructure code and test sources.
- **Exception thrown in advice code or a helper called from advice** — javaagent code must never throw; use `suppress = Throwable.class` as the safety net.
- **`@Advice.OnMethodExit` method named `onEnter`** (or vice versa) — the method name should match the annotation. A mismatch is a copy-paste bug that compiles but confuses readers and may mask intent errors.
- **`.class.getName()` used in `transform()` to reference advice** — see `javaagent-module-patterns.md` for the correct `getClass().getName()` pattern. Flag both `InnerAdvice.class.getName()` and `OuterInstrumentation.class.getName() + "$InnerAdvice"` — any `.class` literal in a `transform()` method triggers unwanted class loading.
