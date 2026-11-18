---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java performance

Apply these rules to Java code. The hot-path allocation rules also apply to Kotlin and Scala.

## Retained lists

When an owner retains an incoming list as long-lived configuration or metadata, including in a
private constructor:

- Take a private `ArrayList` snapshot, including for empty inputs. Do not require callers to pass an
  already-owned list. Declare the field as `List` and choose `ArrayList` where the snapshot is built.
- Use the owned list consistently internally, and treat it as read-only after initialization.
- Do not retain a second field just for an unmodifiable view. If the list is not traversed on a hot
  path, store an unmodifiable view over the owned snapshot. If hot-path code needs to iterate the
  backing list directly, keep that list and wrap it at the exposure boundary.

If the owner builds the final list itself, build it directly as an `ArrayList` rather than copying it
again.

The snapshot isolates the list structure from caller mutation. Using one concrete list
implementation at shared iteration sites enables HotSpot to eliminate iterator allocations.
Mixing implementations at the same site can prevent that optimization.

## Lambdas and method references

Do not hoist a non-capturing lambda or method reference into a field to avoid allocation.
HotSpot caches it at the `invokedynamic` call site.

## Regular expressions

Precompile literal Java regexes when a production call may compile them repeatedly.

No `Pattern` is needed:

- for `String.split` with a simple separator such as `","` or `"\\."`, which takes a fast path
- in test code
- for one-time initialization

## Byte buffers

`Value<ByteBuffer>.getValue()` returns a fresh read-only buffer on each call. Do not add
`.duplicate()` solely to get independent position and limit state.

## Stateless telemetry collaborators

Prefer constructing Java `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`,
`AttributesExtractor`, `SpanNameExtractor`, and `HttpServerResponseMutator` implementations at
registration instead of using singleton `INSTANCE` fields. Retain singletons on per-request hot
paths. Kotlin `object` declarations are outside this rule.
