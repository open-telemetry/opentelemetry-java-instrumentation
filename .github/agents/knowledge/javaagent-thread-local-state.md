# [Javaagent] Temporary ThreadLocal State

## Quick Reference

- Use when: designing, implementing, or reviewing temporary `ThreadLocal` state in javaagent advice
  or helpers
- Requirement: every value that holds operation-specific temporary state needs cleanup on every
  exit, including exceptional exits
- Default for temporary state installed on entry and cleaned up on exit: restore the previous value
- Suppression: only the caller that acquires a `ScopedThreadSuppression` releases it
- Naming: use `current*` for ambient state that belongs to the executing thread, except for
  suppression holders and accessors

## Match cleanup to the lifecycle

Trace every writer, reader, and cleanup point. Thread confinement does not prevent recursion,
constructor chaining, or overlapping advice from replacing or observing outer state.

For temporary state installed on entry and cleaned up on exit, cleanup must restore the previous
value rather than simply remove the entry. Follow this rule even when no current call path is known
to be reentrant. Prefer the allocation-free `ScopedThreadValue`, and carry the value returned by
`set` through `@Advice.Enter`:

```java
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

private static final ScopedThreadValue<Request> currentRequest = new ScopedThreadValue<>();

@Advice.OnMethodEnter(suppress = Throwable.class)
public static @Nullable Request onEnter(Request request) {
  return currentRequest.set(request);
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(@Advice.Enter @Nullable Request previous) {
  currentRequest.restore(previous);
}
```

`restore` removes the entry when `set` returned `null`.

Use `ScopedThreadSuppression` for temporary suppression installed on entry and cleared on exit.
`tryAcquire()` returns `true` to the caller that installed the suppression. Nested calls return
`false`. Carry that result through `@Advice.Enter`, and call `release()` only when the result is
`true`:

```java
import io.opentelemetry.instrumentation.api.internal.ScopedThreadSuppression;

private static final ScopedThreadSuppression receiveSpanSuppression =
    new ScopedThreadSuppression();

@Advice.OnMethodEnter(suppress = Throwable.class)
public static boolean onEnter() {
  return receiveSpanSuppression.tryAcquire();
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(@Advice.Enter boolean acquired) {
  if (acquired) {
    receiveSpanSuppression.release();
  }
}
```

Use `isActive()` when code only needs to check whether suppression is active. A nested caller that
received `false` from `tryAcquire()` must not release suppression owned by an outer caller.

## Name ambient thread state with `current*`

Name a field or accessor `current*` when it represents ambient state that belongs to the executing
thread. This follows conventions such as `currentContext()` and makes the thread confinement clear.
Examples include `currentRequest` and `currentContext()`.

Suppression holders and accessors are a narrow exception. Omit `current` when the suppression name
already identifies the state and purpose, for example `receiveSpanSuppression()` and
`processSpanSuppression()`.

Name the state, not its storage mechanism. In particular, do not use a `*ThreadLocal` accessor name
when the accessor returns a wrapper such as `ScopedThreadSuppression`.

Do not use `current*` for message, request, record, or batch state attached to an object carrier
through `VirtualField`. That state belongs to the carrier and may cross thread boundaries. Name the
field for the carrier state instead, for example `MESSAGE_STATE` or `REQUEST_STATE`.

The entry-and-exit cleanup rules do not apply to long-lived per-thread caches, reusable objects,
counters, persistent maps, or producer/consumer callback handoffs. Manage those values according to
their actual lifetime instead of forcing them into an entry/exit pair.

For a cross-callback handoff, document the producer, the consumer, and the failure path that cleans
up the value when the handoff cannot complete.
