---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java performance

Apply these rules to Java code. The hot-path allocation rules also apply to Kotlin and Scala.

## Hot-path iteration

- On a hot path, do not let one enhanced `for` loop iterate over different collection classes, such as
  an `ArrayList` on some calls and a `LinkedHashSet` or `emptyList()` on others, whether the loop gets
  them from a field, a variable, a parameter, or a helper. HotSpot can fail to eliminate the iterator
  allocation when a loop sees more than one class. This covers sets and map views too.

  If the loop iterates a list field, copy the list where the constructor stores it:

  - Store `new ArrayList<>(list)`, whatever the source: a caller, a builder, a helper, `emptyList()`,
    or `collect(toList())`. Skip the copy when the constructor built the `ArrayList` itself.
  - If the class exposes the list, store `unmodifiableList(new ArrayList<>(list))` and return the
    field, so loops and callers see one class. Do not loop over the backing list separately.
  - Leave helpers, builders, getters, and accessors unchanged, including their `emptyList()`,
    `singletonList()`, and `collect(toList())` calls.

  When a constructor copy does not apply, one fix is to give each collection class its own loop and
  share the per-element work in a method.

## Lambdas and method references

- Do not hoist a non-capturing lambda or method reference into a field to avoid allocation.
  HotSpot caches it at the `invokedynamic` call site.

## Regular expressions

- Precompile literal Java regexes when a production call may compile them repeatedly.

  No `Pattern` is needed:

  - for `String.split` with a simple separator such as `","` or `"\\."`, which takes a fast path
  - in test code
  - for one-time initialization

## Byte buffers

- `Value<ByteBuffer>.getValue()` returns a fresh read-only buffer on each call. Do not add
  `.duplicate()` solely to get independent position and limit state.

## Stateless telemetry collaborators

- Prefer constructing Java `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`,
  `AttributesExtractor`, `SpanNameExtractor`, and `HttpServerResponseMutator` implementations at
  registration instead of using singleton `INSTANCE` fields. Retain singletons on per-request hot
  paths. Kotlin `object` declarations are outside this rule.
