# [Javaagent and Library] Lock Ownership and Critical Sections

## Quick Reference

- Use when: reviewing synchronization around shared javaagent or library instrumentation state
- Review focus: necessary guarantees, actual ownership, bounded critical sections, and publication

These rules apply to library instrumentation as well as javaagent code. The `VirtualField`
recommendation remains javaagent-specific; see [Virtual Fields](javaagent-virtual-fields.md).

## Establish the Requirement First

Start with the observable behavior that must be protected. Identify which supported threads,
callbacks, and extension points can overlap at the exact advice boundary, and whether the library
already serializes or owns completion there. Review the supported versions and configurations,
lazy/eager initialization, nested, skipped, and disabled advice, subclass/delegating construction,
custom implementations, reentrancy, carrier lifetime and reuse, and fallback storage before
declaring thread confinement. Preserve initialization, reuse, and publication guarantees across these
paths. Do not generalize from a standalone helper, arbitrary mock scheduling, or a future that
completes once: advice before a library guard can still run once per attempted completion.

Classify the consequence of a race:

- **Unreachable:** the supported lifecycle proves that the paths cannot overlap.
- **Tolerable:** an explicitly agreed race may omit or leave stale optional metadata until a
  defined recovery boundary, such as the next configuration update.
- **Unacceptable:** the race can corrupt mutable state, leak context, mis-correlate a request,
  reuse a command incorrectly, duplicate or lose required lifecycle effects, or violate the
  agreed metadata accuracy.

Omission is safer than fabricated or cross-operation metadata, but a degraded policy must be
explicit. This guidance does not authorize silently reducing existing behavior or feature
coverage. State whether degradation affects one operation, lasts until another update, or
disables capture permanently. Persistent loss needs its own explicit decision; do not describe it
as transient.

## Choose the Simplest Representation

Before adding synchronization, prefer an observation boundary where the library already supplies the
required ordering, including initialization and cancellation. Carry per-operation state through a
context that survives supported asynchronous delegation instead of compensating for the wrong
boundary with a handoff protocol.

Capture at the point required by the telemetry contract. Separate carrier or peer identity from
derived metadata when immutable values or one snapshot can avoid repeated publication. Account for
selection, retries, and getter stability or extensibility so related attributes stay coherent and
describe the peer actually used. Moving observation time changes semantics; retain earlier capture
when timing matters. A valid snapshot needs revalidation only if later freshness or coherence is
required, not merely because its source changes.

For infrequent configuration or topology updates, compare recomputing the complete target from
canonical state with maintaining incremental deltas, counts, or reservations. Include work already
performed and confirm a safe snapshot is available at the required observation point; preserve key
identity and multiplicity.

After deciding the required guarantee, use the simplest state model that supplies it:

| Requirement | Prefer | Required proof |
| --- | --- | --- |
| Existing ordering or thread confinement is sufficient | Existing plain state | The supported lifecycle supplies ownership and publication |
| One independent value is replaced across threads | One `volatile` immutable or existing value reference | A complete value can be replaced independently |
| One caller must own a genuinely competing effect | One once-claim CAS | The claim is the linearization point |
| Several fields form an un-serialized invariant | One private lock | The owner commits it in a short critical section |

A canonical holder is useful for shared mutable ownership, not mandatory for attaching an independent
value. Sequential or reentrant invocation may require clearing state without requiring CAS.

Consider whether simplifying or removing a protocol is better than merely trimming its allocations:
per-request or per-observation hot paths differ from rare configuration updates. Account for retained
memory, broadened instrumented types, extra objects, and CAS retries along the supported call paths.
Reuse immutable objects and existing state where possible. A configuration-time snapshot is not
automatically a hot-path allocation, and a simple justified lock need not be replaced by complex
lock-free code. Do not invent performance numbers or require a benchmark for every small edit.

## Keep Necessary Locks Safe

The owner of shared state should own a private lock:

```java
private final Object lock = new Object();
private State state;
```

Do not expose it or require callers to synchronize on it. Avoid `this`, `Class`, and
application-owned monitors. Preserve JVM-required class-loading synchronization.

When a lock is necessary, keep it to short reads and writes of instrumentation-owned state. Never
hold an agent lock across the original library method. Do not run callbacks, telemetry, scope
closing, logging, I/O, or waits under it; trace helpers, error paths, virtual dispatch, and
implementation callbacks transitively. A library getter or collection parameter warrants checking
the concrete implementation and supported callers, not an automatic claim that it is arbitrary
external execution. Verified non-overridable leaf access and instrumentation-owned
String-keyed collections need not trigger copying or tokens; uncertain or extensible calls should
be hoisted.

Simple JDK operations on immutable or instrumentation-owned values and bounded local error/state
work are not automatically external. `equals`, `hashCode`, `toString`, collection operations,
and custom implementations still need review.

## Capture and Publish Conditionally

If the observation redesign still requires split capture and publication, and the agreed accuracy
requires rejecting stale updates, reserve ownership or a generation before raced input capture,
including empty-input paths. For example:

```java
Reservation reservation;
synchronized (lock) {
  reservation = state.reserve();
}
Prepared prepared = librarySafeSnapshot(input);
synchronized (lock) {
  state.commit(reservation, prepared);
}
```

Use a library-safe snapshot API or a lifecycle that proves the source cannot change concurrently.
Copying an arbitrary mutable collection or suppressing traversal exceptions does not make it safe.
If exact dynamic capture is necessary, a narrow library commit point may be justified; compare its
instrumentation scope and cost with the agreed metadata policy before adding it. Public method entry
and exit do not necessarily establish mutation order. Preserve the source key multiplicity and object
identity required by the chosen telemetry contract.

Use generation discard only for a superseded, complete replacement snapshot. For additive or
per-key observations, retain and merge independent evidence within the epoch; a latest address
and a carrier identity may be different facts. For optional metadata, require exact ordering only
when the [agreed metadata policy](#establish-the-requirement-first) demands it.

External effects have their own ordering. A generation check cannot undo a stale hook installation
or restore a replaced callback. Define prior-hook ownership, composition, failure, reentrancy, and
reset policy for global hooks. Do not substitute a blanket latest-ticket, queue, or future recipe.

## Separate Terminal Ownership from Enrichment

Separate mandatory terminal ownership, cleanup, error preservation, and scope ownership from optional
enrichment. When the telemetry contract permits end-time available information, the terminal owner
may freeze one coherent snapshot for related final fields, reject later enrichment, and complete
without waiting or handing termination to an updater. Preserve information published before the
freeze and apply the agreed metadata policy to later arrivals. Deferred completion or handoff needs
an independently justified requirement beyond retaining every late attribute.

For mandatory terminal ordering, claim ownership before external effects and preserve thread-affine
scope closure, synchronous API guarantees, reentrancy, and once-only effects. A short metadata lock
may be simpler than a lock-free handoff, but keep external effects out of it:

```java
Work work;
synchronized (lock) {
  work = state.claimTerminalWork();
}
work.finish();
```

A once-claim grants ownership; it does not mean external cleanup is complete. Removing a lock while
waiting on a latch, future, or spin retains the dependency. If the required ordering justifies a
handoff and the lifecycle permits it, an in-flight updater can finish terminal work without
waiting. SDK `Span.end` idempotency does not
prove that instrumenter end, metrics, or callbacks are idempotent. Preserve library-owned timeout
and error identity and synchronous throws. If optional metadata acquisition is moved, its failure
must not bypass required instrumenter or span ending, scope closure, or other cleanup; preserve
application-error behavior rather than silently catching failures.

## Test the Required Guarantees

Tests should establish the selected guarantees on supported reachable call chains. Force races in
those chains when necessary, and cover the agreed metadata policy.
Assert that the intended hook, failure, and ordering path actually ran, not only that a plausible
span or lifecycle result appeared.
Do not require an exhaustive synthetic-race checklist for every change or present an unproven deadlock
as reproduced.
