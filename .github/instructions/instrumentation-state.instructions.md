---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Instrumentation state and synchronization

Apply these rules when instrumentation stores per-object or temporary thread state, synchronizes
shared state, or registers observable metric callbacks. Virtual-field requirements apply to
javaagent instrumentation, not library-only code.

## Choose storage by ownership

- Choose storage based on who owns the state and how long it lives:

  | Requirement | Storage |
  | --- | --- |
  | Javaagent state belongs to a third-party object instance | Typed `VirtualField<Carrier, State>` when [fallback is safe](#lifetime-fallback-and-reuse); otherwise see that section |
  | A shared helper supports callers with different carrier types | Caller-selected virtual field or typed accessor |
  | State is ambient for the current operation or scope | OpenTelemetry `Context`, `Scope`, or scoped thread state |
  | State belongs to an instrumentation-owned wrapper | An ordinary field |
  | Derived metadata varies by class or lookup key | An appropriate cache or `ClassValue` |
  | Entries need a bound independent of key lifetime | A bounded cache |
  | Equal value objects are canonicalized without retention | A synchronized weak interning pool |
  | A wrapper must not strongly retain its delegate | An explicit `WeakReference`, possibly in a virtual field |
  | Library instrumentation runs without agent transformation | Storage supported by the library API and lifecycle |

- Prefer `VirtualField` for instrumentation-owned context, span, suppression, or ownership state
  attached to requests, messages, futures, callbacks, connections, or other third-party instances
  when [fallback storage is safe](#lifetime-fallback-and-reuse). Do not replace real memoization,
  bounded caches, weak delegate links, or value interning mechanically.

## Typed virtual fields

- The caller that knows the library type selects the narrowest stable carrier that owns the state.
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

- Pass a small typed accessor instead when exposing the storage handle is inappropriate. Do not
  centralize associations in `Cache<Object, State>` or replace it with `VirtualField<Object, State>`.
  Broad interfaces are appropriate only when the state belongs to their whole abstraction, such as
  propagated task context on `Runnable`. If no narrower stable carrier exists, document why and
  verify fallback safety and lifecycle behavior as described in
  [Lifetime, fallback, and reuse](#lifetime-fallback-and-reuse).

- The carrier class, value class, and optional field name identify the field. Lookups with the
  same identity share storage across instrumentations. Use a dedicated holder for unrelated
  state or several related values. A common value type such as `Object`, `Boolean`, `String`,
  or `Map` is valid when sharing is deliberate or the field identity is demonstrably unique.

## Virtual-field lookup and registration

- In normal instrumentation, use class literals for the carrier and value types in
  `VirtualField.find(...)` and a string literal for a named field. Muzzle generation registers
  these mappings so the agent can install the virtual field implementations.
- Reuse the resolved handle on production paths that can run repeatedly. Runtime lookup uses
  reflection. A `static final` `SCREAMING_SNAKE_CASE` field, including one on a non-inlined
  advice class, or a holder that already retains the handle, such as a static
  `MessagingTelemetryCarrier`, is sufficient.
- Inlined advice is not exempt; do not rely on the deprecated inline rewrite. Reference the cached
  handle through a helper whose class and member are accessible from the instrumented class.
- A rare runtime lookup for alternative carrier names runs outside advice in an `@NoMuzzle`
  method. Its module implements `ExperimentalInstrumentationModule` and registers every possible
  mapping through `registerVirtualFields(...)`. Do not use this pattern when literals suffice.

## Lifetime, fallback, and reuse

- Virtual fields follow identity, not `equals()`. Setting `null` removes a fallback value:

  ```java
  STATE.set(carrier, null);
  ```

- Clear per-operation state after its last consumer is done. On failure or abandonment, clear it
  when no remaining consumer needs it. For example, a deferred Lettuce endpoint write still reads
  the caller context stored on an `AsyncCommand` to parent its span after that command is
  cancelled. If the last consumer may never run, leaving the state is acceptable only when the
  carrier is not reused and the value does not strongly reference the carrier. Clear the state
  before a pooled or reused carrier represents another operation. Garbage collection does not
  prevent stale state on reuse.

- A `VirtualField` value must not strongly reference its own carrier, directly or indirectly,
  unless every supported terminal path removes the entry with `set(carrier, null)` or breaks the
  reference path. When field injection is disabled or unavailable, the fallback map holds the value
  strongly. A back-reference keeps the weak key alive, so garbage collection cannot remove the
  entry.

  Trace concrete paths from the value to its carrier, including mutable holders that other
  instrumentation writes to later, possibly only under non-default settings. A value type that
  could reach arbitrary objects, such as `Context`, is not a problem by itself. For example, with
  span suppression set to `none`, a stored `Context` can reach its Undertow exchange through a
  response saved in servlet async state. If cleanup cannot be guaranteed, store detached data, use
  a weak back-reference if the state remains valid after the reference is cleared, or keep the
  state in storage owned by the carrier.

  Trace strong references from each concrete value that can be stored at runtime, including lambda
  captures and any enclosing instances held by inner classes. Check for a path back to the value's
  own carrier. If a value reaches another carrier, check that carrier's fallback values in turn
  for cycles across entries. If A's value keeps B alive and B's value keeps A alive, neither weak
  key can clear. A connection's value can retain a completed request in either storage mode, even
  without a cycle. The clearing rules above still apply.

  If the analysis rules out strong paths back to a value's own carrier and cycles across entries,
  do not add a GC test or a separate JVM run with field injection disabled just to confirm that
  result. If safety depends on when state is cleared or replaced, test the relevant lifecycle path.

- Individual `get` and `set` calls do not make a compound read-modify-write atomic. Establish whether
  supported concurrent updates need an invariant before choosing synchronization or ownership.
  Do not choose a cache solely for `computeIfAbsent`.

## Observable metric callbacks

- **Registration ownership.** The SDK bundled with the javaagent holds the registered callback
  strongly. Identify who owns the registration and when it ends. Verify that every supported
  shutdown or replacement path reaches the instrumentation's cleanup. Close the returned handle
  and remove any long-lived registry entries for that registration. Dropping the handle does not
  unregister the callback. If a shutdown path misses that cleanup, fix the hook rather than
  weakening the reference.

- **Retention after unregistration.** A closed SDK handle still holds the callback. Retaining it
  is not a defect when an equivalent native implementation retains the same state after removal,
  as native Micrometer meters and HikariCP's Micrometer tracker do. Clearing the handle is still
  worthwhile when nothing else references the captured state: it frees `stats` captured by
  `stats::completedFor` in a removed Micrometer `FunctionCounter` that the application still
  holds, but not a HikariCP pool still referenced by a retained `HikariDataSource`.

- **Collection without explicit shutdown.** If an object or class loader should be collectible
  while the registration is live, trace every strong reference to it from the SDK or other
  long-lived state. Check what the callback and its measurement functions capture, the class loaders
  that define the callback and its helpers, and any registry entries.

  A weak reference to the measured object is different from a weak reference to the callback.
  If you hold the callback itself weakly, give it an owner that keeps it alive while it should
  still report. Decide whether to skip recording or unregister when the target disappears.

  For example, `Gauge.builder(name, queue, Queue::size)` in Micrometer observes the queue weakly
  by default. The queue can be collected while the gauge stays registered, so the bridge skips
  recording once the queue is gone. Capturing the queue in the measurement function would keep
  it alive and defeat the weak reference.

## Temporary thread state

- When an invocation changes temporary thread state, restore the previous value on every exit,
  including exceptions. Trace what writes and reads the state and which calls can nest. Thread
  confinement does not prevent a nested call from replacing outer state.

- If an invocation skips changing temporary thread state, leave that state untouched on exit.

- If an inner operation must not inherit an enclosing value, hide it while the inner operation runs
  and restore it afterward.

- Keep acquisition and cleanup in the advice pair or helper that installs the state. Restore or
  release before fallible completion work that does not need the temporary state. Use
  `try`/`finally` when that work needs the state.

- Prefer `ScopedThreadValue` for new scoped values when the previous value is enough for cleanup.
  `set()` returns that value directly without allocating a separate restore token. In a simple
  advice pair, carry it through `@Advice.Enter`:

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

- Release temporary suppression only when this invocation acquired it. Prefer
  `ScopedThreadSuppression` for new suppression code. In a simple advice pair, carry
  `tryAcquire()`'s result through `@Advice.Enter`:

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

  Use `isActive()` for read-only checks.

- For a correctness finding about temporary thread state in an audit or review, name a supported
  call path that discards enclosing state, leaves temporary state installed, or uses state that
  belongs to another operation.

- Keep a holder private in the narrowest owner. If several classes in one module need direct access,
  use a private field in `*Singletons`, exposed by a same-named zero-argument accessor and
  statically imported by callers. Cross-module state uses a focused shared bootstrap owner.

- Name ambient executing-thread state `current*`, such as `currentRequest`. Suppression names such
  as `receiveSpanSuppression` need no `current` prefix. Name the state, not its storage mechanism;
  do not use `*ThreadLocal` for a wrapper accessor. Carrier-attached state may cross threads and does
  not use `current*`.

- These entry/exit rules do not apply to long-lived thread caches, reusable objects, counters,
  persistent maps, or callback handoffs. Manage their actual lifetime. For a handoff, document its
  producer, consumer, and failure cleanup.

## Supported concurrency and lock scope

- Evaluate synchronization against the supported initialization, completion, cancellation, retry,
  nested, and reuse paths that can overlap. Account for subclasses, custom implementations, fallback
  storage, and skipped advice. Prefer library ordering or an existing operation context over a
  separate handoff protocol.

- Publish related mutable state as a unit. One immutable value can use a safely published reference;
  several fields may need one short private lock. Use a once-claim CAS when supported cross-thread
  completion paths race. For same-thread reentrancy, consuming state before invoking reentrant code
  may suffice. Do not add locks, generations, reservations, or CAS loops for hypothetical races.

- A complete old immutable snapshot of descriptive connection metadata may briefly be acceptable
  during client or pool reconfiguration. Refresh at a stable lifecycle boundary, publish safely,
  and converge on the latest snapshot once updates stop. Do not mix fields from different snapshots
  or substitute a connected peer for a configured target. This allowance does not cover context,
  span lifecycle, suppression, completion ownership, request/response pairing, status, errors, or
  cleanup.

- Instrumentation locks protect short state reads, writes, and ownership decisions. Keep original
  library calls, instrumenter/SDK calls, application callbacks, overridable getters, collection
  traversal, logging, I/O, waits, and scope closure outside them. Capture work under the lock,
  release it, then perform external effects.

  JVM class-loading locks are an exception. A library-object monitor may also be necessary to
  coordinate with library or other instrumentation code; verify and document the owner and lock
  ordering. Preserve thread-bound scope closure, immediate application errors, and library timeouts
  and error behavior.
