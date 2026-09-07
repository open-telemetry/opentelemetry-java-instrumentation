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

## Separate Capture from Completion

Keep these two questions separate:

1. **What information belongs to the operation?** Capture it at the lifecycle point required by the
   telemetry contract.
2. **Which call is responsible for finishing the operation?** If supported terminal paths can
   overlap, claim cleanup and completion exactly once. If the lifecycle guarantees one terminal
   path, no claim is needed.

If capturing information and publishing it require separate steps, use a snapshot only when
supported callbacks can overlap or external completion will read mutable state after the lock is
released. Reserve the update before reading input that can change. Publish only a valid snapshot,
and abort or recover the reservation if capture fails or the updater disappears. Use generation
checks only to discard a superseded complete replacement; preserve independent facts, such as
carrier identity and peer metadata, when the contract requires them.

Do not hold the metadata lock while performing instrumenter, SDK, callback, or scope operations.
When terminal paths overlap, claim the work under the lock and perform the external effects
afterward. When there is only one terminal path, perform those effects without an unnecessary
claim. In both cases, preserve thread-affine scope closure, synchronous throws, application errors,
and library-owned timeout and error identity.

Global hooks need the same care: define who owns the previous hook, how hooks compose, and what
happens on reentrancy, reset, or partial failure. Do not report a hook as installed until the
complete transition succeeds, and roll back changes that were made before a failure.

## Test Reachable Guarantees

Tests should cover the supported lifecycle paths that can overlap, especially initialization,
cancellation, reuse, asynchronous completion, terminal races, and failure paths. Assert that the
intended hook, cleanup, and ordering path ran, not merely that plausible telemetry was produced.
