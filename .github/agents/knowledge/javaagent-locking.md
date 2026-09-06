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
lazy/eager initialization, subclass or custom implementations, and reentrant paths before
declaring thread confinement. Do not generalize from a standalone helper, arbitrary mock
scheduling, or a future that completes once: advice before a library guard can still run once per
attempted completion.

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

After deciding the required guarantee, use the simplest state model that supplies it:

| Requirement | Prefer | Required proof |
| --- | --- | --- |
| Existing ordering or thread confinement is sufficient | Existing plain state | The supported lifecycle supplies ownership and publication |
| One independent value is replaced across threads | One `volatile` immutable or existing value reference | A complete value can be replaced independently |
| One caller must own a genuinely competing effect | One once-claim CAS | The claim is the linearization point |
| Several fields form an un-serialized invariant | One private lock | The owner commits it in a short critical section |

Do not allocate a holder, lock, atomic, token, defensive copy, or retry protocol merely because a
synthetic race can be imagined. A canonical holder is useful when shared mutable ownership requires
one; it is not mandatory for attaching an independent value. Preserve carrier reuse,
initialization, and fallback-storage behavior. Existing sequential or reentrant invocation may
require clearing state without requiring CAS.

Consider cost and scope explicitly: per-request or per-observation hot paths differ from rare
configuration updates. Account for nested, skipped, and disabled advice paths, retained memory,
broadened instrumented types, extra objects, and CAS retries. Reuse immutable objects and existing
state where possible. A configuration-time snapshot is not automatically a hot-path allocation,
and a simple justified lock need not be replaced by complex lock-free code. Do not invent
performance numbers or require a benchmark for every small edit.

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

Only when the agreed accuracy requires rejecting stale updates, reserve ownership or a generation
before raced input capture, including empty-input paths. For example:

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
instrumentation scope and cost with an explicitly approved omission policy before adding it. Public
method entry and exit do not necessarily establish mutation order. Preserve the source key
multiplicity and object identity required by the chosen telemetry contract.

Use generation discard only for a superseded, complete replacement snapshot. For additive or
per-key observations, retain and merge independent evidence within the epoch; a latest address
and a carrier identity may be different facts. Do not require exact ordering for optional metadata
whose agreed policy permits omission or staleness. Such data may remain stale until a future
update, so say that explicitly.

External effects have their own ordering. A generation check cannot undo a stale hook installation
or restore a replaced callback. Define prior-hook ownership, composition, failure, reentrancy, and
reset policy for global hooks. Do not substitute a blanket latest-ticket, queue, or future recipe.

## Separate Terminal Ownership from Enrichment

Do not add deferred terminal state or waiting solely to guarantee every late attribute. If terminal
ordering is genuinely required, claim ownership before external effects and preserve thread-affine
scope closure, synchronous API guarantees, reentrancy, and once-only effects:

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
and error identity and synchronous throws.

## Review and Test the Supported Lifecycle

Trace actual callers and all relevant access paths, including nested, skipped, disabled,
subclass/delegating construction, fallback storage, and carrier reuse. For hooks, keep ownership
and class-loading caveats explicit but concise.

Tests should establish the selected guarantees on supported reachable call chains. Force races in
those chains when necessary, and cover any explicitly approved omission or stale-metadata policy.
Do not make an exhaustive synthetic-race checklist mandatory for every change, and do not treat a
mockable slow leaf getter or arbitrary mutable input as evidence without a supported caller.
Report confidence accurately; do not present an unproven deadlock as reproduced.
