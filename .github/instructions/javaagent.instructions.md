---
applyTo: "instrumentation/**/javaagent/**/*.java,instrumentation/**/javaagent*/**/*.kt,instrumentation/**/bootstrap/**/*.java,instrumentation/**/bootstrap/**/*.kt,instrumentation/**/javaagent-integration-tests/**/*.java,instrumentation/**/javaagent-integration-tests/**/*.kt,javaagent/**/*.java,javaagent/**/*.kt,javaagent-bootstrap/**/*.java,javaagent-bootstrap/**/*.kt,javaagent-tooling/**/*.java,javaagent-tooling/**/*.kt,examples/**/instrumentation/**/*.java,examples/**/instrumentation/**/*.kt,smoke-tests/extensions/extension/**/*.java,smoke-tests/extensions/extension/**/*.kt,testing/agent-exporter/**/*.java,testing/agent-exporter/**/*.kt,testing-common/integration-tests/**/*.java,testing-common/integration-tests/**/*.kt"
---

# Javaagent instrumentation

Apply these checks to executable advice and the helpers it calls. Inspect registration and
library lifecycle before deciding whether an exception applies.

## Advice and matching

- A new `InstrumentationModule` needs SPI registration and a compatible `TypeInstrumentation`;
  in a project with multiple independently selected modules, each needs a distinct name and
  the correct Muzzle selection. `CallDepth` suppresses recursively instrumented calls only
  when entry increments and exit decrements on every applicable path.
- Executable `@Advice.OnMethodEnter` and `@Advice.OnMethodExit` methods with fallible bodies
  need `suppress = Throwable.class`; inspect helper calls too. Exclude test code, intentional
  internal infrastructure, provably throw-free methods such as a literal return, and dummy
  helper-injection advice registered with a `none()` matcher. Do not demand suppression for
  code that never runs. Javaagent helpers must not let instrumentation failures break an
  application; log suppressed unexpected failures where they occur unless they are expected
  optional probes.
- Exit advice owning a `Scope` or temporary state must run on exceptional method exits and
  release it, preferably before fallible completion work. Return-only advice that processes
  `@Advice.Return` without cleanup should omit `onThrowable = Throwable.class`; on an
  exceptional exit the return value is null or zero. Do not hide acquisition of an open raw
  `Scope` behind a general helper; advice that carries a raw `Scope` through `@Advice.Enter`
  acquires it directly. An ordinary nullable `AdviceScope` is fully initialized when present,
  with non-null context and scope internals. Nullable internals belong only in a non-null
  placeholder needed to carry bookkeeping state through exit advice.
- Keep advice as static nested classes with no instance fields and static methods. Every advice
  class needs class-level `@SuppressWarnings("unused")`. In `transform()`, reference nested
  advice by `getClass().getName() + "$AdviceName"`; resolving
  `InnerAdvice.class` or a library class literal from the agent loader can cause premature
  class loading. Do not name an exit advice method `onEnter` or vice versa. Method
  signature matchers may use accessible `java.base` types available at the minimum
  supported Java version; keep `named(...)` for instrumented-library types.
  With dynamic advice typing, match only signatures that guarantee assignability.
- Do not require `isDeclaredBy` on a Byte Buddy method matcher by default. Use it only
  when a type matcher covers several types but advice targets a member declared by one
  specific type; a name and signature that already identify the method need no extra
  restriction. `isMethod()` is redundant after a non-empty `named(...)` or
  `namedOneOf(...)` matcher. Keep it when the name can be empty, because `named("")`
  can match constructors and class initializers.
- Use `Java8BytecodeBridge` only for supported API calls directly in annotated advice;
  helper methods use the normal API even when nested inside the advice class. Add a
  `classLoaderMatcher()` only for a real version boundary Muzzle cannot distinguish.
  Verify every version-landmark class against the artifact versions and keep its role explicit:
  a positive floor uses `// added in X.Y`, a positive ceiling uses `// removed in X.Y`, and a
  negated exclusion class uses `// added in X.Y`. A positive class that supplies both bounds
  states both. If the landmark comes from a different artifact, identify that artifact in the
  comment; label a non-version optional-artifact check as an artifact-presence gate.
  Javaagent production code may import both stable and incubating semconv constants
  directly when their key name and type match; the agent vendors those artifacts.

## Attached and temporary state

- When javaagent code attaches instrumentation-owned state to the identity of a
  third-party object, use a typed `VirtualField<Carrier, State>` selected by the caller
  that knows the carrier. A shared helper may accept the typed handle or accessor; never
  replace `Cache<Object, State>` with `VirtualField<Object, State>`. This does not apply to
  metadata memoization, bounded caches, weak delegate links, value interning, or
  non-javaagent library code.
- Inside inlined advice use `VirtualField.find(...)` at the point of use; outside inlined
  advice cache it in a `static final` field. Non-inlined advice cannot call `find(...)`
  directly. The `(carrier, value)` pair identifies the field. Clear per-operation state
  on completion, abandonment, and carrier reuse; in weak-key fallback storage, a value
  that strongly references its carrier can keep the carrier alive.
- For temporary per-operation thread state installed on entry, carry the previous value
  through `@Advice.Enter` and restore it on every exit, including exceptions. Release
  `ScopedThreadSuppression` only if the entry acquired it. Do not apply entry/exit cleanup
  rules to long-lived thread caches or producer/consumer callback handoffs.
- For shared state, first establish which supported calls can overlap or reenter. Publish
  related mutable state together and keep instrumentation locks off original library calls,
  SDK operations, callbacks, logging, waits, and scope closure. A complete old snapshot of
  descriptive metadata may briefly be acceptable; stale context, completion ownership,
  request/response pairing, or cleanup is not. Do not request CAS or generation counters
  for unsupported hypothetical races.

## Holder conventions

- In `*Singletons`, `*SpanNaming`, and similar holders, a zero-argument accessor directly
  returning a stored collaborator has the same lower-camel name as its field, without `get`.
  Callers static-import that accessor. This does not apply to methods that compute values,
  take arguments, or perform work; do not static-import verb-named helper methods on this
  basis. Uppercase constant-like fields may be exposed directly.
