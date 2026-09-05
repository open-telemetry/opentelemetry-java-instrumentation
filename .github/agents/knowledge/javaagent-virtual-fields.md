# [Javaagent] Virtual Fields

## Quick Reference

- Use when: javaagent code associates instrumentation state with third-party object instances, or
  introduces a weak reference, weak-key cache/map, identity-keyed registry, or per-object side table
- Review focus: choosing `VirtualField`, carrier and value types, shared-helper boundaries, cleanup,
  reuse, concurrency, and fallback-map retention

## Prefer `VirtualField` for State Attached to Library Objects

Use `VirtualField<T, F>` when javaagent instrumentation needs to attach instrumentation-owned state
of type `F` to an instance of a third-party type `T` and the state should live no longer than that
instance.

Typical carriers include requests, responses, messages, records, callbacks, tasks, futures,
connections, and sessions. Typical values include `Context`, span state, suppression or ownership
state, and a dedicated holder containing several related values.

This is the repository's purpose-built alternative to an external
`Cache<Object, InstrumentationState>`, `WeakHashMap`, or identity-keyed map. The javaagent normally
injects storage into eligible carrier implementations and falls back to a weak-key map when field
injection is unavailable.

Look for the design intent rather than a particular API. Comments such as "do not keep the message
alive," "keyed by object identity," or "remember state until this request is collected" are strong
signals that `VirtualField` should be considered.

## Storage Decision

| Requirement | Preferred storage |
| --- | --- |
| State belongs to a third-party object instance in javaagent instrumentation | `VirtualField` |
| State is shared logic, but each caller knows a different carrier type | Caller-owned `VirtualField` or typed accessor passed to the shared helper |
| State is ambient for the current operation or scope | OpenTelemetry `Context`, `Scope`, or the existing scoped suppression mechanism |
| State belongs to an instrumentation-owned wrapper or helper | An ordinary field on that object |
| Values are derived metadata cached by `Class`, `ClassLoader`, name, or another lookup key | The appropriate cache or `ClassValue` pattern |
| Cache entries need a size bound independent of key lifetime | A bounded cache |
| Equal value objects are canonicalized without retaining them | A synchronized weak interning/canonicalization pool |
| A callback or wrapper must not strongly retain another object in its object graph | An explicit `WeakReference`, possibly stored in a `VirtualField` |
| Library instrumentation runs without javaagent transformation | Choose based on the library API and lifecycle; do not assume field injection |

Do not replace every weak reference or weak cache mechanically. `Cache.weak()` is appropriate for
real memoization and class-loader-sensitive metadata. A `WeakHashMap<K, WeakReference<K>>` can be
appropriate for interning equal values without retaining them. A `WeakReference` is also appropriate
when the reference itself controls reachability, rather than implementing an external association
from a carrier to instrumentation state.

## Keep Carrier Selection with the Typed Caller

Shared bootstrap or common helpers often cannot name every library carrier type. Do not solve that
boundary by centralizing all state in `Cache<Object, F>` or by declaring
`VirtualField.find(Object.class, F.class)`.

Let the instrumentation-specific caller select a narrow, stable carrier class or interface and pass
the resulting handle to generic logic:

```java
private static final VirtualField<Message, MessageState> MESSAGE_STATE =
    VirtualField.find(Message.class, MessageState.class);

public static void onMessage(Message message, MessageState state) {
  MessagingStateHelper.set(MESSAGE_STATE, message, state);
}
```

The shared helper can remain generic and free of library types:

```java
public static <T> void set(VirtualField<T, MessageState> field, T carrier, MessageState state) {
  field.set(carrier, state);
}
```

This pattern is already used by the executors instrumentation, whose bootstrap helpers accept a
`VirtualField<T, PropagatedContext>` chosen by the javaagent module. When passing the handle would
expose too much storage behavior, pass a small typed accessor with `get`, `set`, and any required
domain operations instead.

Avoid `Object` as the carrier class. It discards the type that determines where field-backed storage
can be installed and makes the mapping apply far more broadly than intended. Generic helper methods
may use a type parameter, but the `VirtualField.find(...)` call should identify the actual library
carrier class or interface.

## Carrier and Value Types Identify the Field

Treat the `(carrier class, value class)` pair passed to `VirtualField.find(...)` as the identity of
the virtual field. Instrumentations using the same pair intentionally share the same storage.

Do not reuse a common value type such as `Object`, `Boolean`, `String`, or `Map` for unrelated state
on the same carrier type when that could collide with another instrumentation. Introduce a dedicated
holder type when the state has its own meaning or contains multiple related values:

```java
private static final VirtualField<Message, MessagingTelemetryState> TELEMETRY_STATE =
    VirtualField.find(Message.class, MessagingTelemetryState.class);
```

Use a common value type only when sharing that exact field is deliberate or the carrier/value pair
is otherwise demonstrably unique.

## Lookup Placement

- Call `VirtualField.find(Carrier.class, Value.class)` with class literals in normal javaagent
  instrumentation. Muzzle uses the literals to discover and register the mapping.
- Inside inlined `@Advice` methods, call `VirtualField.find(...)` directly where the field is used.
  The call is rewritten during transformation; do not extract it into a helper or field merely to
  cache the lookup.
- Non-inlined advice methods (`inline = false`) must not call `VirtualField.find(...)` because those
  calls are not rewritten and `VirtualFieldChecker` rejects them. Put the result in a `static final`
  `SCREAMING_SNAKE_CASE` field on a non-advice helper or singleton, then reference that handle from
  the advice method.
- Outside advice, including helper and singleton classes, also store the result in a `static final`
  `SCREAMING_SNAKE_CASE` field so runtime lookup happens once.
- Non-literal class arguments are rejected in normal instrumentation. A rare runtime lookup for
  alternative carrier class names must run outside advice in an `@NoMuzzle` method. The
  instrumentation module must implement `ExperimentalInstrumentationModule` and override
  `registerVirtualFields(...)` to register every possible carrier/value pair. Do not copy that
  specialized pattern when class literals can represent the types.

The containing instrumentation module must use muzzle generation so the carrier/value mapping is
registered and calls inside inlined `@Advice` can be rewritten.

## Lifetime, Reuse, and Retention

`VirtualField` follows object identity, not `equals()`. Setting a field to `null` removes the value
from fallback storage:

```java
STATE.set(carrier, null);
```

Do not rely only on garbage collection when the logical lifetime is shorter than the carrier's
lifetime. Clear the field after the state is consumed and on failure paths that abandon the
operation.

Weak-key semantics do not solve object reuse. If a pooled request, mutable message holder, or other
carrier can represent a later logical operation, clear or replace its state at that lifecycle
boundary so the next operation cannot observe stale state.

The fallback implementation has weak keys and strong values. A value that strongly references its
carrier can therefore keep the carrier reachable through the fallback map. Avoid that object graph
or store a weak reference to the back-reference when it is genuinely required. Field-backed storage
can collect a carrier/value cycle, but code must remain safe when field injection is disabled or
unavailable.

## Concurrency

Individual `get` and `set` operations are safe to use from instrumented code, but a compound
read-modify-write sequence is not an atomic map operation:

```java
State state = STATE.get(carrier);
STATE.set(carrier, update(state));
```

If multiple threads can update the same carrier, make the attached state provide the required
atomicity or synchronization, or redesign the ownership transition. Do not choose `Cache` solely
for `computeIfAbsent` without first deciding whether concurrent updates and duplicate construction
are actually valid for the instrumentation.

For synchronization around compound instrumentation state transitions, see
[Lock Ownership and Critical Sections](javaagent-locking.md).

## Review Guidance

Flag a new weak or identity-keyed registry when all of the following are true:

1. The code is javaagent instrumentation or shared code used by javaagent instrumentation.
2. The key is a third-party object instance rather than a lookup key such as `Class` or
   `ClassLoader`.
3. The value is instrumentation state associated with that exact instance, not a derived value being
   memoized.
4. The instrumentation-specific caller can identify a suitable carrier class or interface.
5. `VirtualField` can preserve the required lifecycle and concurrency semantics.

Recommend moving storage selection to the typed caller when a shared helper currently accepts
`Object`. Do not demand one global `VirtualField<Object, F>`.

Do not flag legitimate metadata caches, bounded caches, value-equality interning pools, weak
callback/delegate links, or non-javaagent library code merely because they use weak storage. When
the carrier type, lifecycle, or concurrency requirement is unclear, investigate callers before
commenting and prefer silence over a speculative replacement.
