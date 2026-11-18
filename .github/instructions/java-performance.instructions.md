---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java performance

Apply these rules to Java code. The hot-path allocation rules also apply to Kotlin and Scala.

## Hot-path allocation

Avoid allocations on the hot path whenever possible, including iterators. A for-each loop over
a plain Java array does not allocate an iterator.

Non-allocating Stream API usage is acceptable, but it may not fit the surrounding code. When
choosing an implementation, weigh the complexity of pre-sizing `collect` sinks and the
allocations from capturing `forEach` lambdas.

## Retained lists

When retaining an incoming list as long-lived configuration or metadata:

- Take a private `ArrayList` snapshot, including for empty inputs.
- Use that owned copy consistently internally, and treat it as read-only after initialization.
- Preserve unmodifiable views when exposing the list.
- Enhanced-for traversal is fine; arrays or indexed loops are not required.

A fresh `ArrayList` that is already privately owned does not need another copy.

The snapshot isolates the list structure from caller mutation and gives empty and nonempty
inputs the same stored implementation.

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
