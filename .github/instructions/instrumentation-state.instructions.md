---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Instrumentation state and synchronization

Apply these rules when instrumentation stores per-object or temporary thread state, or synchronizes
shared state. Virtual-field requirements apply to javaagent instrumentation, not library-only code.

## Choose storage by ownership

| Requirement | Storage |
| --- | --- |
| Javaagent state belongs to a third-party object instance | Typed `VirtualField<Carrier, State>` |
| A shared helper supports callers with different carrier types | Caller-selected virtual field or typed accessor |
| State is ambient for the current operation or scope | OpenTelemetry `Context`, `Scope`, or scoped thread state |
| State belongs to an instrumentation-owned wrapper | An ordinary field |
| Derived metadata varies by class or lookup key | An appropriate cache or `ClassValue` |
| Entries need a bound independent of key lifetime | A bounded cache |
| Equal value objects are canonicalized without retention | A synchronized weak interning pool |
| A wrapper must not strongly retain its delegate | An explicit `WeakReference`, possibly in a virtual field |
| Library instrumentation runs without agent transformation | Storage supported by the library API and lifecycle |

Use `VirtualField` for instrumentation-owned context, span, suppression, or ownership state
attached to requests, messages, futures, callbacks, connections, or other third-party instances.
It normally injects storage into the carrier and falls back to weak-key storage when injection is
unavailable. Do not replace real memoization, bounded caches, weak delegate links, or value
interning mechanically.

## Typed virtual fields

The caller that knows the library type selects the narrowest stable carrier that owns the state.
A shared bootstrap helper need not name that type:

```java
private static final VirtualField<Message, MessageState> MESSAGE_STATE =
    VirtualField.find(Message.class, MessageState.class);

public static void onMessage(Message message, MessageState state) {
  MessagingStateHelper.set(MESSAGE_STATE, message, state);
}
```

```java
public static <T> void set(VirtualField<T, MessageState> field, T carrier, MessageState state) {
  field.set(carrier, state);
}
```

Pass a small typed accessor instead when exposing the storage handle is inappropriate. Do not
centralize associations in `Cache<Object, State>` or replace it with `VirtualField<Object, State>`.
Broad interfaces are appropriate only when the state belongs to their whole abstraction, such as
propagated task context on `Runnable`. If no narrower stable carrier exists, document why and
verify fallback and lifecycle behavior.

The `(carrier class, value class)` pair identifies the field. The same pair intentionally shares
storage across instrumentations. Use a dedicated holder for unrelated state or several related
values rather than colliding on `Object`, `Boolean`, `String`, or `Map` value types. A common value
type is valid when sharing is deliberate or the pair is demonstrably unique.

## Virtual-field lookup and registration

- In normal instrumentation, call `VirtualField.find(Carrier.class, State.class)` with class
  literals so Muzzle generation discovers and registers the pair.
- Inside inlined advice, call `find(...)` at the point of use; transformation rewrites the call.
  Do not extract it merely to cache the lookup.
- Non-inlined advice cannot call `find(...)` directly. Cache the handle in a `static final`
  `SCREAMING_SNAKE_CASE` field on a non-advice helper or singleton and reference it from advice.
  Outside advice, cache lookup in the same way.
- A rare runtime lookup for alternative carrier names runs outside advice in an `@NoMuzzle`
  method. Its module implements `ExperimentalInstrumentationModule` and registers every possible
  pair through `registerVirtualFields(...)`. Do not use this pattern when class literals suffice.

The containing module needs Muzzle generation to register mappings and rewrite inlined lookups.

## Lifetime, fallback, and reuse

Virtual fields follow identity, not `equals()`. Setting `null` removes a fallback value:

```java
STATE.set(carrier, null);
```

Clear per-operation state after consumption, on abandonment and failure, and before a pooled or
reused carrier represents another operation. Garbage collection does not prevent stale state on
reuse.

Fallback storage has weak keys and strong values. A value that strongly references its carrier
can keep the carrier alive. Avoid that graph, or use a weak back-reference when genuinely needed.
Field-backed storage can collect a carrier/value cycle, but fallback storage must remain safe.

Individual `get` and `set` calls do not make a compound read-modify-write atomic. Establish whether
supported concurrent updates need an invariant before choosing synchronization or ownership.
Do not choose a cache solely for `computeIfAbsent`.

## Temporary thread state

Trace writers, readers, cleanup, and recursion. Thread confinement does not prevent nested calls
from replacing outer state.

For state installed on entry and cleaned on exit, prefer allocation-free `ScopedThreadValue`.
Carry the previous value through `@Advice.Enter` and restore it on every exit, even when no current
caller is known to reenter:

```java
@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
public static @Nullable Request onEnter(@Advice.Argument(0) Request request) {
  return currentRequest().set(request);
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
public static void onExit(@Advice.Enter @Nullable Request previous) {
  currentRequest().restore(previous);
}
```

Here `currentRequest()` returns a `ScopedThreadValue<Request>`. `restore(null)` removes the entry.
Use `ScopedThreadSuppression` for temporary suppression. Carry `tryAcquire()`'s result through
entry and release only when this invocation acquired it:

```java
@Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
public static boolean onEnter() {
  return receiveSpanSuppression().tryAcquire();
}

@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
public static void onExit(@Advice.Enter boolean acquired) {
  if (acquired) {
    receiveSpanSuppression().release();
  }
}
```

Use `isActive()` for read-only checks. Advice calls `set`/`restore` or `tryAcquire`/`release`
directly so ownership stays visible; do not hide these mutations behind semantic enter/exit wrappers.
Restore or release before fallible completion work that does not need the temporary state.
Use `try`/`finally` when the work intentionally requires that state to remain installed.

Keep a holder private in the narrowest owner. A single instrumentation class owns its holder;
several classes in one module use a private field in `*Singletons`, exposed by a same-named
zero-argument accessor and statically imported by callers. Cross-module state uses a focused
shared bootstrap owner.

Name ambient executing-thread state `current*`, such as `currentRequest`. Suppression names such
as `receiveSpanSuppression` need no `current` prefix. Name the state, not its storage mechanism;
do not use `*ThreadLocal` for a wrapper accessor. Carrier-attached state may cross threads and does
not use `current*`.

These entry/exit rules do not apply to long-lived thread caches, reusable objects, counters,
persistent maps, or callback handoffs. Manage their actual lifetime. For a handoff, document its
producer, consumer, and failure cleanup.

## Supported concurrency and lock scope

Before adding or removing synchronization, establish which supported initialization, completion,
cancellation, retry, nested, and reuse paths can overlap. Account for subclasses, custom
implementations, fallback storage, and skipped advice. Prefer library ordering or an existing
operation context over a new handoff protocol.

Publish related mutable state as a unit. One immutable value can use a safely published reference;
several fields may need one short private lock. Use a once-claim CAS when supported cross-thread
completion paths race. For same-thread reentrancy, consuming state before invoking reentrant code
may suffice. Do not add locks, generations, reservations, or CAS loops for hypothetical races.

A complete old immutable snapshot of descriptive connection metadata may briefly be acceptable
during client or pool reconfiguration. Refresh at a stable lifecycle boundary, publish safely,
and converge on the latest snapshot once updates stop. Do not mix fields from different snapshots
or substitute a connected peer for a configured target. This allowance does not cover context,
span lifecycle, suppression, completion ownership, request/response pairing, status, errors, or
cleanup.

Instrumentation locks protect short state reads, writes, and ownership decisions. Keep original
library calls, instrumenter/SDK calls, application callbacks, overridable getters, collection
traversal, logging, I/O, waits, and scope closure outside them. Capture work under the lock,
release it, then perform external effects.

JVM class-loading locks are an exception. A library-object monitor may also be necessary to
coordinate with library or other instrumentation code; verify and document the owner and lock
ordering. Preserve thread-bound scope closure, immediate application errors, and library timeouts
and error behavior.
