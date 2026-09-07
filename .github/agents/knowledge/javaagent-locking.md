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

## Preserve Capture and Terminal Semantics

If capture and publication are split, reserve ownership before reading raced input and commit only a
valid snapshot. Every reservation needs an abort or recovery path if capture fails or the updater
disappears.

Use generation discard only for superseded complete replacements. Preserve independent additive
evidence, such as carrier identity and peer metadata, when the contract requires it.

Separate terminal ownership from optional enrichment. Claim mandatory cleanup and completion exactly
once, then perform instrumenter, SDK, callback, and scope effects outside the metadata lock.
Preserve thread-affine scope closure, synchronous throws, application errors, and library-owned
timeout and error identity.

Global hook installation also needs an explicit ownership, composition, failure, reentrancy, and
reset policy. Do not mark a hook installed until the complete transition succeeds, and define
rollback for partial installation.

## Test Reachable Guarantees

Tests should cover the supported lifecycle paths that can overlap, especially initialization,
cancellation, reuse, asynchronous completion, terminal races, and failure paths. Assert that the
intended hook, cleanup, and ordering path ran, not merely that plausible telemetry was produced.
