# [Javaagent and Library] Locking Instrumentation State

Use this guidance when synchronization protects state added by javaagent or library
instrumentation.

## Establish the Supported Lifecycle

Before adding or removing synchronization, trace the actual supported instrumentation callbacks:

- Can initialization, completion, cancellation, retries, nested calls, or callbacks overlap?
- Can the instrumented object or library object be reused?
- Does the library already handle the operation one at a time or provide a context that survives an
  asynchronous handoff?
- Can subclasses, custom implementations, fallback storage, or disabled or skipped advice change
  the ordering?
- Does the state need exact metadata, or is omission or staleness explicitly acceptable?

Preserve existing initialization, state visibility, context, cleanup, and reuse guarantees. Do not
assume that calls stay on one thread based on a helper, mock, or one-shot future when instrumentation
can run once per attempted operation.

## Prefer the Existing Lifecycle

Prefer the library's ordering or an operation context over a new handoff protocol. Use `VirtualField`
for javaagent state attached to third-party objects; keep the related updates safe as one unit.

For shared mutable state:

- use a safely published reference, such as a `volatile` field, when replacing one immutable value;
- use a once-claim CAS when one of several callers must finish an operation;
- use one short private lock when several fields must be updated together.

Descriptive connection metadata used only for attributes or span names may be temporarily stale
while a long-lived client, connection, or pool is reconfigured. A span created during an update may
use either the complete previous immutable snapshot or the complete new one. Once updates stop,
subsequent spans must converge on the latest snapshot. Refresh the snapshot at a stable client or
pool lifecycle boundary and publish it safely.

This allowance does not apply to context propagation, span lifecycle, suppression, completion
ownership, request/response pairing, status or error attribution, or cleanup. It does not permit
fields from different snapshots or change what the metadata means. Temporarily reporting the
previous configured target can be acceptable; substituting a connected peer for a configured target
is not.

Do not add generation counters, CAS loops, handoff protocols, or instrumentation of lower-level
collection mutations solely to preserve the exact transition point.

Do not add a lock, CAS, generation, copy, or reservation merely because an unsupported hypothetical
race can be imagined.

## Keep Instrumentation Locks Narrow

Base the lock scope on the supported library lifecycle. Do not retain a lock or add a snapshot,
handoff, or completion claim for calls that the library does not make concurrently in legitimate
usage.

Locks used by instrumentation should protect only short reads, writes, and decisions about which
call proceeds. Do not hold them across:

- original library calls;
- instrumenter or SDK calls;
- callbacks, logging, I/O, waits, or scope closure;
- overridable getters, collection traversal, or callbacks through application code.

Capture the work in a local value under the lock, release it, and perform the external effect
afterward.

JVM class-loading locks are an exception. A monitor on a library object or instrumented object can
also be valid when instrumentation must coordinate with the library or with other instrumentation
using that object, but the owner and order in which locks are acquired must be verified and
documented.

Keep external effects out of state locks: do not hold one while performing instrumenter, SDK,
callback, logging, I/O, waits, or scope operations. If supported completion paths overlap, claim
completion under the lock and perform external effects afterward. Otherwise, do not add a
completion claim just to guard against hypothetical duplicate calls. Preserve closing scopes on the
thread where they were opened, exceptions that must be thrown immediately, application errors, and
the library's timeout and original error.
