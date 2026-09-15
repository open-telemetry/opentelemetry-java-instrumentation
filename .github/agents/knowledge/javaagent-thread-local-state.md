# [Javaagent] Temporary ThreadLocal State

## Quick Reference

- Use when: designing, implementing, or reviewing temporary `ThreadLocal` state in javaagent advice
  or helpers
- Requirement: every value that holds operation-specific temporary state needs cleanup on every
  exit, including exceptional exits
- Default for temporary state installed on entry and cleaned up on exit: restore the previous value

## Match Cleanup to the Lifecycle

Trace every writer, reader, and cleanup point. Thread confinement does not prevent recursion,
constructor chaining, or overlapping advice from replacing an outer value.

For temporary state installed on entry and cleaned up on exit, cleanup must restore the previous
value rather than simply remove the entry. Follow this rule even when no current call path is known
to be reentrant. Prefer the allocation-free `ScopedThreadLocal`, and carry the value returned by
`set` through `@Advice.Enter`:

```java
private static final ScopedThreadLocal<Request> CURRENT_REQUEST = new ScopedThreadLocal<>();

@Advice.OnMethodEnter(suppress = Throwable.class)
public static @Nullable Request onEnter(Request request) {
  return CURRENT_REQUEST.set(request);
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
public static void onExit(@Advice.Enter @Nullable Request previous) {
  CURRENT_REQUEST.restore(previous);
}
```

`restore` removes the entry when `set` returned `null`.

The restore-previous rule does not apply to long-lived per-thread caches, reusable objects,
counters, persistent maps, or producer/consumer callback handoffs. Manage those values according to
their actual lifetime instead of forcing them into an entry/exit pair.

For a cross-callback handoff, document the producer, the consumer, and the failure path that cleans
up the value when the handoff cannot complete.
