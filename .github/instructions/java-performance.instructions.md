---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java performance

Apply these rules to Java code. The hot-path allocation rules also apply to Kotlin and Scala.

## Retained lists

When a constructor stores a list, store `new ArrayList<>(list)`, whatever the source: a caller, a
builder, a helper, `emptyList()`, or `collect(toList())`. The copy prevents outside mutation and gives
loops a consistent `ArrayList`, which helps HotSpot avoid iterator allocations.

If the class exposes the list, wrap it once in the constructor, for example
`unmodifiableList(new ArrayList<>(list))`, and return the field directly. Do not wrap a list that
never leaves the class.

Copy only in the constructor. Leave helpers, builders, getters, accessors, and loops unchanged,
including their `emptyList()`, `singletonList()`, and `collect(toList())` calls.

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
