# Instrumenter Context and Scope Patterns

## Quick Reference

- Use when: code calls `Instrumenter.shouldStart(...)`, `Instrumenter.start(...)`, or
  `Context.makeCurrent()`
- Review focus: paired-call parent-context data flow and a real consumer for every active scope

## Keep `shouldStart` and `start` on the Same Parent

For one attempted instrumentation operation/request,
`Instrumenter.shouldStart(parentContext, request)` and
`Instrumenter.start(parentContext, request)` must receive the same intended parent context and
request.

Derive the final parent context before calling `shouldStart`. Do not reassign that context, call
`Context.current()` again, or otherwise select a different parent before `start`. This also applies
when `start` is hidden behind a lifecycle helper such as `InstrumenterUtil.startAndEnd(...)`.

```java
// Wrong: shouldStart checks currentContext, but start uses extractedParentContext.
if (!instrumenter.shouldStart(currentContext, request)) {
  return;
}
Context context = instrumenter.start(extractedParentContext, request);

// Correct: derive the intended parent first and use it for both calls.
Context parentContext = propagator.extract(currentContext, request, getter);
if (!instrumenter.shouldStart(parentContext, request)) {
  return;
}
Context context = instrumenter.start(parentContext, request);
```

When reviewing, compare the arguments as a pair and trace local assignments between the calls.
Matching variable names are not sufficient if the variable is reassigned. If a factory, listener,
or lifecycle helper separates the calls, trace the stored context and request through that local
handoff.

## Require a Consumer for Every Active Scope

Call `makeCurrent()` only when code during the resulting `Scope` lifetime reads
`Context.current()` or otherwise depends on the active context. Valid consumers include:

- instrumented application code that executes before the scope closes
- a callback, listener, delegate, or downstream helper that relies on the current context
- propagation or library integration code documented to read the current context implicitly

`Instrumenter.shouldStart`, `Instrumenter.start`, and `Instrumenter.end` receive their contexts
explicitly and are not themselves consumers of `Context.current()`. A scope surrounding only those
lifecycle calls is redundant and should be removed.

Review the complete lifetime from `makeCurrent()` through `Scope.close()`. For ByteBuddy advice,
that lifetime can include the instrumented application method between enter and exit advice, even
though no consumer is visible in the advice source. Preserve activation around application
execution, callbacks, and helpers when they may require the started context current. Remove a scope
only when its entire lifetime is locally provable to have no consumer; report cases requiring
runtime or instrumentation-domain knowledge instead of speculating.

## What to Flag in Review

- **Different parent contexts for one `shouldStart` / `start` attempt** — including a context
  variable reassigned between calls or a mismatch hidden by a lifecycle helper.
- **Different requests for one `shouldStart` / `start` attempt** — both calls must describe the
  same operation.
- **`makeCurrent()` with no consumer during the scope lifetime** — explicit `Instrumenter`
  lifecycle calls alone do not justify activation.
- **Scope removal that crosses application or callback execution without proving that execution is
  independent of `Context.current()`** — retain the scope or obtain the required domain knowledge.
