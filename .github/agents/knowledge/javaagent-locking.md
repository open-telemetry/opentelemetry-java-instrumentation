# [Javaagent and Library] Locking Instrumentation State

Use this guidance when synchronization protects state added by javaagent or library
instrumentation.

## Establish the Supported Lifecycle

Before adding or removing synchronization, trace the actual supported advice chain:

- Can initialization, completion, cancellation, retries, nested calls, or callbacks overlap?
- Can the carrier or library object be reused?
- Does the library already serialize the operation or provide a context that survives asynchronous
  delegation?
- Can subclasses, custom implementations, fallback storage, or disabled or skipped advice change
  the ordering?
- Does the state need exact metadata, or is omission or staleness explicitly acceptable?

Preserve existing initialization, publication, context, cleanup, and reuse guarantees. Do not infer
thread confinement from a helper, mock, or one-shot future when advice can run once per attempted
operation.

## Prefer the Existing Ownership Boundary

Prefer the library's ordering or an operation context over a new handoff protocol. Use `VirtualField`
for javaagent state attached to third-party objects; make the attached state itself provide the
required atomicity.

For shared mutable state:

- use immutable replacement or `volatile` for one independently replaceable value;
- use a once-claim CAS for one competing terminal effect;
- use one short private lock when multiple fields form an invariant.

Do not add a lock, CAS, generation, copy, or reservation merely because an unsupported synthetic race
can be imagined.

## Keep Instrumentation Locks Narrow

Base the lock scope on the supported library lifecycle. Do not retain a lock or add a snapshot,
handoff, or terminal claim for calls that the library does not make concurrently in legitimate
usage.

Instrumentation-owned locks should protect only short reads, writes, and ownership transitions. Do
not hold them across:

- original library calls;
- instrumenter or SDK calls;
- callbacks, logging, I/O, waits, or scope closure;
- extensible getters, collection traversal, or virtual dispatch.

Claim or snapshot the work under the lock, release it, and perform the external effect afterward.

JVM class-loading locks are an exception. A library-owned or carrier-owned monitor can also be valid
when instrumentation must coordinate with the library or with other instrumentation using the same
carrier, but that ownership and lock ordering must be verified and documented.

Keep external effects out of metadata locks: do not hold one while performing instrumenter, SDK,
callback, logging, I/O, waits, or scope operations. If supported terminal paths overlap, claim
completion under the lock and perform external effects afterward. Otherwise, do not add a terminal
claim just to guard against hypothetical duplicate calls. Preserve thread-affine scope closure,
synchronous throws, application errors, and library-owned timeout and error identity.

Global hooks need explicit ownership, composition, failure, reentrancy, and reset behavior. Do not
report a hook as installed until the complete transition succeeds, and roll back changes that were
made before a failure.

## Test Reachable Guarantees

Tests should cover the supported lifecycle paths that can overlap, especially initialization,
cancellation, reuse, asynchronous completion, terminal races, and failure paths. Assert that the
intended hook, cleanup, and ordering path ran, not merely that plausible telemetry was produced.
