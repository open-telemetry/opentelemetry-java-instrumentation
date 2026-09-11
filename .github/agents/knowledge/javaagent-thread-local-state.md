# [Javaagent] Temporary ThreadLocal State

## Quick Reference

- Use when: javaagent advice or helpers place temporary state in a `ThreadLocal`
- Review focus: call-path evidence for nesting, reentrancy, overlapping advice, callbacks, and
  cleanup on every exit path
- Default: remove a one-shot value after use; restore a previous value only when the state is a
  stack-like dynamic scope

## Choose Cleanup from the Call Path

Trace the instrumented call from the write to every read and cleanup. Check the library and advice
callers, not just the helper that owns the `ThreadLocal`.

Ask:

- Can the instrumented method recurse or invoke another matched method before cleanup?
- Can constructor chaining or overlapping advice write the same `ThreadLocal`?
- Can application, library, or instrumentation callbacks reenter the path on the same thread?
- Does an existing value suppress the inner operation, or may the inner operation replace it?
- Must an outer value become visible again after the inner call returns?

Thread confinement alone does not answer these questions. A single thread can still have nested
calls.

## Restore Stack-Like Dynamic Scopes

Preserve and restore the previous value when the call path supports real nesting and the outer value
must survive an inner scope:

```java
Value previous = state.get();
state.set(value);
try {
  return call();
} finally {
  if (previous == null) {
    state.remove();
  } else {
    state.set(previous);
  }
}
```

Stack-like examples include:

- `MongoClusterSettings.LegacySrvTargetScope`, where a scoped legacy SRV target is restored after
  URI processing
- `RegistryCapturingInvoker`, where a Dubbo delegate call may run code that reads or replaces the
  captured registry address
- `SpringSchedulingTaskTracing`, where callers temporarily change a wrapping flag and restore the
  caller's prior suppression state

## Remove One-Shot State

Use `ThreadLocal.remove()` when the value is a one-shot handoff, a current-operation marker that
rejects nesting, or a suppression flag whose call path cannot overlap. Do not add a scope object and
previous-value field for an unsupported hypothetical nested call.

One-shot examples include:

- `JedisRequestContext` and the `JedisPipelineContext` variants, which track one active request or
  batch and clear it on exit
- `HbaseClientState`, which passes temporary call state between advice points
- `RemoteMessageState` in Pekko and `MessageListenerContext` in Pulsar, which mark a bounded
  serialization or listener-processing path
- `ExecutorAdviceHelper`, whose temporary propagation state follows explicit submit or thread-switch
  boundaries
- Vert.x prepared-statement advice, which hydrates temporary state for one call and clears it after
  use

A boolean does not decide the policy. A nestable suppression guard may need restoration, while a
non-overlapping suppression flag should be removed.

## Review Guidance

Review every writer, reader, and cleanup point for the `ThreadLocal`. Ask for previous-value
restoration only when a supported call path can write an inner value while an outer value is active
and later needs the outer value again. Recursion, nesting, constructor chaining, overlapping advice,
and callbacks can create that requirement.

If no such path exists, prefer removal and the smaller state model. Ensure cleanup runs for normal
and exceptional exits in either case.
