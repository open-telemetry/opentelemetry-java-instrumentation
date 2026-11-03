---
applyTo: "instrumentation/**/javaagent/**/*.java,instrumentation/**/javaagent*/**/*.kt,instrumentation/**/bootstrap/**/*.java,instrumentation/**/bootstrap/**/*.kt,instrumentation/**/javaagent-integration-tests/**/*.java,instrumentation/**/javaagent-integration-tests/**/*.kt,javaagent/**/*.java,javaagent/**/*.kt,javaagent-bootstrap/**/*.java,javaagent-bootstrap/**/*.kt,javaagent-tooling/**/*.java,javaagent-tooling/**/*.kt,examples/**/instrumentation/**/*.java,examples/**/instrumentation/**/*.kt,smoke-tests/extensions/extension/**/*.java,smoke-tests/extensions/extension/**/*.kt,testing/agent-exporter/**/*.java,testing/agent-exporter/**/*.kt,testing-common/integration-tests/**/*.java,testing-common/integration-tests/**/*.kt"
---

# Javaagent advice and scope ownership

Apply these rules to executable Byte Buddy advice and the helpers it calls. Inspect how advice is
registered before deciding whether a test, infrastructure, throw-free, or helper-injection
exception applies.

## Advice structure and transformation

- Keep advice in static nested classes with no instance fields and static entry points.
  Advice classes and entry points remain public for invokedynamic method-handle dispatch.
  Give each advice class class-level `@SuppressWarnings("unused")`, not per-method suppressions.
- Entry and exit method names must agree with their annotation. Do not name
  `@Advice.OnMethodExit` advice `onEnter`, or `@Advice.OnMethodEnter` advice `onExit`.
- Explicitly set `inline = false` on both `@Advice.OnMethodEnter` and `@Advice.OnMethodExit` in
  production. The only production exceptions are
  `instrumentation/internal/internal-class-loader` and
  `instrumentation/internal/internal-lambda`. Test fixtures may intentionally exercise inlined
  or mixed advice. Mixing inlined and non-inlined advice disables automatic helper isolation.
- Use ordinary OpenTelemetry APIs in non-inlined advice and helpers. Static interface calls in
  inlined advice targeting pre-Java-8 class files go through a Java-8-compiled helper.
  Helpers are ordinary compiled methods even when nested in an advice class.
- Omit explicit `typing = Assigner.Typing.DYNAMIC` on `@Advice.AssignReturned` mappings,
  including nested argument and field mappings, in the standard transformation pipelines.
  Their `ForceDynamicallyTypedAssignReturnedFactory` supplies it. This does not configure
  advice parameter bindings such as `@Advice.Argument` or `@Advice.Return`; custom pipelines
  need separate verification. Dynamic typing never makes an incompatible replacement safe.

## Failures and exceptional exits

Executable enter and exit advice with fallible bodies needs `suppress = Throwable.class`,
including when the possible failure is in a helper. Instrumentation and its helpers must not
let unexpected failures break application code. Log suppressed unexpected failures where they
occur unless they are expected optional probes.

Suppression is unnecessary for:

- test code;
- intentional internal infrastructure, such as class-loading/lambda support where swallowing
  an error would corrupt operation;
- provably throw-free bodies, such as returning a literal or reading a field with no possible
  null dereference;
- dummy advice registered with `none()` solely to trigger helper injection, because it never runs.

Do not classify library calls, iteration, or reflection as throw-free. A helper call in a dummy
advice body does not make suppression useful.

Exit advice owning a scope, span, call-depth increment, or temporary state runs on exceptional exits
with `onThrowable = Throwable.class` and releases what entry acquired. Return-only advice with no
cleanup omits `onThrowable`: on an exceptional exit, `@Advice.Return` receives null or zero rather
than a real result. Suppression and exceptional-exit selection solve different problems.

## Raw scope ownership

In ordinary code, acquire scopes at the call site and use try-with-resources:

```java
try (Scope ignored = context.makeCurrent()) {
  doWork();
}
```

When paired advice carries a raw `Scope` through `@Advice.Enter`, acquire it directly in enter
advice and close that same scope in exit advice. A general helper returns the context or other
state, not an open raw scope that obscures who closes it.

```java
@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
public static @Nullable Scope onEnter(@Advice.Argument(0) Request request) {
  Context context = startContext(request);
  if (context == null) {
    return null;
  }
  return context.makeCurrent();
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
public static void onExit(@Advice.Enter @Nullable Scope scope) {
  if (scope != null) {
    scope.close();
  }
}
```

Complete fallible setup before `makeCurrent()` and acquire last, immediately before returning.
If independent fallible work must follow acquisition, close the acquired scope if that work fails.

Close the scope at the first safe point before fallible completion work. This prevents a suppressed
failure from leaving context attached and avoids keeping operation context current unnecessarily.
Use `finally` only when earlier completion work intentionally needs that context.

Repository instrumentation assumes `Context.makeCurrent()` and `Scope.close()` do not throw.
Do not add defensive catches or `finally` blocks solely for hypothetical failures from those calls;
this is a coding assumption, not a guarantee about arbitrary `ContextStorage` implementations.

## AdviceScope state

A dedicated `AdviceScope` may clearly own acquisition and closure through `start()` and `end()`.
Use those names for new ordinary factories/completion methods rather than one-off `create()` names.
There are two justified shapes.

### Nullable state for conditional instrumentation

Use `@Nullable AdviceScope` by default when entry may skip instrumentation. `start()` returns null
when preconditions fail or `shouldStart` is false. When present, context and scope internals are
non-null; `end()` closes the scope unconditionally. The null check belongs in exit advice.

```java
public static class AdviceScope {
  private final Request request;
  private final Context context;
  private final Scope scope;

  private AdviceScope(Request request, Context context) {
    this.request = request;
    this.context = context;
    this.scope = context.makeCurrent();
  }

  @Nullable
  public static AdviceScope start(Request request) {
    Context parent = Context.current();
    if (!instrumenter().shouldStart(parent, request)) {
      return null;
    }
    return new AdviceScope(request, instrumenter().start(parent, request));
  }

  public void end(@Nullable Throwable throwable) {
    scope.close();
    instrumenter().end(context, request, null, throwable);
  }
}
```

Paired exit advice runs on exceptional exits and calls `end()` only when the returned state is
non-null. Do not combine nullable outer state with nullable tracing internals and a second
scope-null guard.

### Non-null placeholder for bookkeeping

Nullable internals are justified in a non-null placeholder when exit still needs bookkeeping
without a started tracing scope, such as call depth or wrapped arguments/listeners. Keep guards
around only the optional tracing work.

Asynchronous completion changes where a span ends, not these two state models. Close thread-bound
scopes on the thread where they opened and end asynchronous spans at their actual completion.
