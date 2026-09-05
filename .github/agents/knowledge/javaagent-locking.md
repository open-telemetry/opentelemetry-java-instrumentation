# [Javaagent] Lock Ownership and Critical Sections

## Quick Reference

- Use when: reviewing synchronization around shared javaagent instrumentation state
- Review focus: private lock ownership, short bounded critical sections, and consistent state publication

## Keep Lock Ownership Local

The class that owns instrumentation state should also own its lock. Keep both private so the
guarded state transitions are understandable within one class:

```java
private final Object lock = new Object();
private State state;
```

Do not expose or return the lock, pass it to callers, or require callers to synchronize on it.
Avoid `this`, `Class`, or an application-owned object as a monitor; external code can observe or
acquire those monitors and create an implicit lock-order dependency.

A private lock is not enough by itself. The code executed while holding it must also stay within
the instrumentation-owned critical section.

## Keep Critical Sections Bounded

Under the lock, do only short local reads and writes of instrumentation-owned state. A private
helper is acceptable when its call path is fully understood and bounded; do not use a helper as a
way to hide an uncontrolled call.

Do not call application or library code, arbitrary suppliers, listeners, callbacks, logging,
exporting, I/O, or waiting while holding an instrumentation lock. Such calls can block, acquire
another lock, or reenter the instrumentation. Another path that takes those locks in the reverse
order can deadlock, while reentrant code can observe partially updated state or violate an
invariant. The risk is transitive: inspect the calls made by helpers, not only the method named in
the synchronized block.

Simple JDK operations on instrumentation-owned primitive or immutable data are not categorically
forbidden. Be cautious with `equals`, `hashCode`, `toString`, and collection operations: they can
dispatch to application implementations or otherwise invoke code outside the intended critical
section.

## Keep External Calls Outside the Lock

When an external operation is needed, capture the necessary instrumentation-owned data under the
lock and perform the operation afterward:

```java
Snapshot snapshot;
synchronized (lock) {
  snapshot = state.snapshot();
}
Result result = externalOperation(snapshot);
```

Moving the call outside the lock does not automatically make a refresh or update correct. A newer
state change may occur while the operation runs, allowing an older snapshot or result to overwrite
newer state. Use an explicit ordering or version check with retry/discard behavior, or choose a
state model that makes the publication safe.

## Review Guidance

- Check that one class owns a private lock and every guarded state transition.
- Trace the complete call path inside the critical section for callbacks, blocking, lock
  acquisition, logging, and telemetry export.
- Reject APIs that expose a lock or make callers synchronize on an instrumentation monitor.
- Keep external reads and callbacks separate from guarded state updates, while preserving a
  consistent publication order.
- Do not turn this into a blanket requirement to remove locks or use CAS; choose the simplest
  state model whose ownership and ordering can be reasoned about locally.
