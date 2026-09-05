# [Javaagent and Library] Lock Ownership and Critical Sections

## Quick Reference

- Use when: reviewing synchronization around shared javaagent or library instrumentation state
- Review focus: ownership, lifecycle, publication, bounded critical sections, and external effects

These rules make ownership and ordering explicit; they do not require removing every lock. The
critical-section guidance applies to library instrumentation too. The `VirtualField`
recommendation remains javaagent-specific; see [Virtual Fields](javaagent-virtual-fields.md).

## Choose the Simplest Correct State Model

Start with lifecycle and ownership. State the happens-before relationship and linearization point before choosing a primitive:

| Requirement | Prefer | Required proof |
| --- | --- | --- |
| Thread confinement or immutable state after publication | No lock | The owner and publication boundary are stable |
| One independent value shared across threads | `volatile` | A complete value can be replaced independently |
| One caller must win | CAS or an atomic once-claim | The claim linearizes ownership and effects follow it |
| Several fields form one invariant | One private lock | The owner commits the invariant in a short section |

Preserve JVM-required class-loading synchronization and bounded static initialization when they provide the needed publication and class-loader behavior. They are lifecycle constraints, not a
generic preferred primitive. Do not add atomics, queues, futures, or state machines unless their
ownership and failure semantics are clearer than the simpler model.

## Keep Ownership Local

The class that owns shared instrumentation state should own its private lock:

```java
private final Object lock = new Object();
private State state;
```

Do not expose the lock or require callers to synchronize on it. Avoid `this`, `Class`, or an
application-owned object as a monitor because external code can acquire it and create an implicit
lock-order dependency.

For javaagent state attached to a third-party carrier, use one canonical typed `VirtualField`
holder when the lifecycle permits it. Individual `get` and `set` operations do not atomically
initialize a holder. Install it before the carrier escapes when possible. Lazy installation is
safe only when every accessor of that field pair coordinates the complete get/create/set sequence,
including merge, replace, clear, failure, subclass, delegating, and reentrant construction paths.
Constructor installation proves ownership only when all other access paths preserve that holder;
a private lock in each wrapper does not coordinate them, and a strong registry is not a substitute.

After a terminal claim, clearing the association is safe only when no newer operation can attach to
the same carrier. Otherwise clear or replace state at the actual reuse boundary, after in-flight
users can no longer retain the old holder.

## Bound the Critical Section

When reset or replacement can race input capture, reserve the owner or generation before reading
external input, including empty-input paths. Then commit the owned transition under the lock and
perform external work afterward:

```java
Reservation reservation;
synchronized (lock) {
  reservation = state.reserve();
}
Prepared prepared = prepareOutsideLock(input);
Work work;
synchronized (lock) {
  work = state.commit(reservation, prepared);
}
work.runOutsideLock();
```

The preparation step needs a library-safe snapshot API or a configuration lifecycle that proves
the source cannot change concurrently; do not copy an arbitrary mutable collection outside the
lock and call it a snapshot. If the source is live and mutable, observe a narrow library commit or
lifecycle point instead. Public method entry and exit do not necessarily establish mutation order.
Never hold the private lock across the original library method. Preserve the source key
multiplicity and object identity needed by the telemetry projection.

External work includes library getters, caller-owned collection snapshots, arbitrary suppliers,
scope closing, instrumenter/span/exporter calls, callbacks, I/O, logging, and waiting. A private
helper is safe only when its complete path remains bounded and instrumentation-owned. Bounded
local error/state work and simple JDK operations on immutable or instrumentation-owned values are
fine; `equals`, `hashCode`, `toString`, collection operations, and error paths need review because
they can dispatch to application code. Do not hide external work in cache callbacks or retryable
CAS functions.

## Commit Ownership Before Effects

For completion and cleanup, commit terminal ownership before invoking external code:

```java
Work work;
synchronized (lock) {
  if (state.ended()) {
    return;
  }
  work = state.claimAndDetach();
}
work.finish();
```

This protects terminal reentrancy. It does not by itself solve a start-versus-finish race:
startup and finish need a shared explicit protocol or safe prepublication. Removing a lock while
waiting on a latch, future, or spin still retains the dependency; when the lifecycle permits,
defer terminal ownership to the in-flight updater without waiting. Preserve thread-affine scope
closure, synchronous throws, library-owned timeout and error identity, callback ordering, and any
guarantee that a visible completion is already reflected in local state. A once-claim grants
ownership; it does not mean external cleanup has completed.

## Publish the Right Result

A generation check can discard a superseded whole replacement snapshot. It cannot undo a stale hook
installation, restore a replaced callback, or order an external effect that already happened.
Distinguish reset or retry epochs from mergeable observations within an epoch:

- For a latest-complete replacement, publish a tokenized snapshot and discard an older token.
- For additive or per-key fanout observations, retain and merge each independent observation;
  dropping an earlier result can falsely report one peer. A latest address and a carrier identity
  may be different facts and must not be collapsed.

Do not impose a global “latest ticket” algorithm where the state is a mergeable delta. For global
enable/reset and hook installation, define prior-hook ownership, composition, failure, reentrancy,
and reset policy explicitly. A boolean CAS, queue, future, or retry loop is not a substitute, and
progress must not depend on waiting for the class-loading callback that holds JVM synchronization.

## Review and Test the Lifecycle

Trace all accessors and complete call paths, including callbacks, error paths, fallback storage, and
carrier reuse. Keep JVM-required loading locks and bounded owned operations when they are correct;
setup-only execution does not make external logging or library work safe under a lock.

Add deterministic race and reentrancy coverage for initialization, start/finish, one-time
termination, cleanup, carrier reuse, callback or hook ordering, failure/reset, stale publication,
and both field-backed and fallback `VirtualField` storage. Test ownership and ordering contracts,
not merely the presence of a lock or CAS. Identify the owner, publication boundary, happens-before
edge, and linearization point; match severity to evidence. A source-level lock-order or
reentrancy hazard is actionable, but an unproven deadlock is not reproduced.
