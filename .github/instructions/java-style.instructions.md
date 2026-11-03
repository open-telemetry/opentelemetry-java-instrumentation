---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java conventions

Apply these rules to Java code, using the stated exceptions for advice, tests, and public APIs.
The null-comparison and hot-path allocation rules also apply to Kotlin and Scala.

- **Visibility**: use the narrowest access that works. Static fields should be private,
  except for constant-like fields with uppercase names. Add `// visible for
  testing` if a production member has broader visibility solely for direct
  test access. Keep advice classes and their static entry points public for
  invokedynamic method-handle dispatch. Ordinary helpers may use narrower access when
  their callers and class-loader boundaries permit it. A single public class
  in a module may be needed for Javadoc.
- **Public API**: make public API classes `final` where possible. Declare methods `final`
  only in non-final public API classes. Name public getters `get*` or `is*` for booleans.
  Javaagent singleton accessors
  directly returning stored fields are an exception to getter naming. Public
  implementation classes that are not API belong in an `.internal` package.
- **Fields**: declare fields `final` where possible. Use `SCREAMING_SNAKE_CASE` for
  immutable value constants, not just because a field is `static final`. These include
  fixed `Duration` values, even when computed from configuration at startup, and canonical
  `INSTANCE`, `EMPTY`, and `NOOP` values. Use uppercase for private `static final` arrays
  of constant or immutable values when they are neither exposed outside the class nor mutated
  after initialization. Semantic keys and handles, including `AttributeKey`, `ContextKey`,
  `MethodHandle`, and `Pattern`, are candidates for uppercase names, not an
  unconditional requirement. A `static final VirtualField` *must* be uppercase
  regardless of visibility or runtime initialization. Loggers, instrumenters,
  helpers, and caches stay lower camel case. Collections exposed outside a class must be unmodifiable;
  `Arrays.asList` is fixed-size but not unmodifiable. Do not add a wrapper
  to a private non-escaping collection.
- **Catch parameters**: in catch clauses only, name a used exception `e`,
  or `f` if nested under an `e`; use `t` for a used `Throwable`. `error`
  is acceptable for a specific `*Error` subtype. Unused catch parameters
  are `ignored`, or `ignore` when `ignored` would shadow an outer catch.
- **Suppressions**: place `@SuppressWarnings` on the one member that needs
  it, or on the class when multiple members need the same warning
  suppressed. Byte Buddy advice classes are an exception:
  `@SuppressWarnings("unused")` belongs on the class. Use
  `"deprecation"` rather than `"OtelDeprecatedApiUsage"` for verified
  intentional deprecated API use.
- **Attribute setters**: `AttributesBuilder.put`, `Span.setAttribute`,
  `SpanBuilder.setAttribute`, and `LogRecordBuilder.setAttribute` ignore a
  null value. A guard around a directly passed nullable value, such as a
  `String` or boxed primitive, is redundant, except when an `Integer`
  passed for `AttributeKey<Long>` selects the `int` overload and would
  unbox null. Retain a guard protecting a dereference or derived computation.
- **Regular expressions**: precompile literal Java regexes when a
  production call may repeatedly compile them. `String.split` has a fast
  path for simple separators such as `","` and `"\\."`; no `Pattern` is needed
  there, in test code, or for one-time initialization.
- **Hot-path allocation**: avoid allocations on the hot path whenever possible, including iterators.
  A for-each loop over a plain Java array does not allocate an iterator.
  Non-allocating Stream API usage is acceptable but may not fit the surrounding code.
  Consider the complexity of pre-sizing `collect` sinks and allocations from
  capturing `forEach` lambdas when choosing an implementation.
- **Lambda allocation**: do not hoist a non-capturing lambda or
  method reference into a field to avoid allocation. HotSpot caches it at
  the `invokedynamic` call site.
- **Byte buffers**: `Value<ByteBuffer>.getValue()` returns a fresh read-only
  buffer on each call. Do not add `.duplicate()` solely to get independent
  position and limit state.
- **Nullability**: `TextMapGetter.get`/`getAll` and `TextMapSetter.set`
  receive a nullable carrier under the upstream SDK contract. Annotate
  it and guard carrier-specific logic; pure delegation to another
  getter/setter needs the annotation but not another guard. `keys` does
  not have this nullable-carrier contract. Do not add `@Nullable` in
  test code.
- **Stateless telemetry collaborators**: prefer constructing Java
  `TextMapGetter`, `TextMapSetter`, `*AttributesGetter`,
  `AttributesExtractor`, `SpanNameExtractor`, and
  `HttpServerResponseMutator` implementations at registration instead
  of singleton `INSTANCE` fields. Retain singletons on per-request hot
  paths; Kotlin `object` is unrelated.
- **Class organization**: order static fields (final before non-final),
  static initializers, instance fields (final before non-final),
  constructors, methods, then nested classes. Place callers before
  callees. A private static initializer helper or `static` block may
  immediately follow its field. Place public static factory entry points
  immediately above constructors; in static utility classes, place the
  private constructor after all methods.
- **Null comparisons**: prefer `value == null` and `value != null` to
  `null == value` and `null != value`.
- **Optional**: keep `java.util.Optional` usage to a minimum. Avoid it in public API
  signatures. Avoid it on the hot path unless the instrumented library uses it. See
  [Writing a Java library with better experience (slide 12)](https://speakerdeck.com/trustin/writing-a-java-library-with-better-experience?slide=12)
  for the API design rationale.
- **Other conventions**: do not add `final` to parameters or locals. Do not flip
  `value.equals(CONSTANT)` solely for speculative null safety.
  Omit explicit type arguments on generic method calls when type inference
  already works; keep them where compilation actually requires them.
- **Conditional expressions**: avoid directly nested ternaries, even when
  parenthesized. Use named intermediate values, `if`/`else`, or early returns,
  keeping simple ternaries where they improve readability. A ternary inside a
  separate method-call argument does not count as direct nesting. In tests,
  nested ternaries may remain inline in attribute-assertion expected values.

## General nullability guidance

This general annotation guidance is aspirational and may not reflect the current codebase.

Annotate nullable production parameters and fields with `javax.annotation.Nullable`, which the
`otel.java-conventions` Gradle plugin includes as a `compileOnly` dependency.
`@NonNull` is unnecessary because non-null is the default.

Public APIs should check for null parameters even when they are not annotated as nullable.
Internal APIs do not need these defensive checks.

All modules should apply the `otel.nullaway-conventions` Gradle plugin for basic nullable-usage
validation:

```kotlin
plugins {
  id("otel.nullaway-conventions")
}
```

## Nullable carriers and attribute overloads

The upstream propagation interfaces have these carrier contracts:

| Interface | Method | Nullable parameter |
| --- | --- | --- |
| `TextMapGetter<CarrierT>` | `get(CarrierT, String)` | `carrier` |
| `TextMapGetter<CarrierT>` | `getAll(CarrierT, String)` | `carrier` |
| `TextMapGetter<CarrierT>` | `keys(CarrierT)` | none |
| `TextMapSetter<CarrierT>` | `set(CarrierT, String, String)` | `carrier` |

A getter that delegates directly to another `TextMapGetter` can pass the nullable carrier
through without a second guard:

```java
@Override
@Nullable
public String get(@Nullable C carrier, String key) {
  return delegate.get(carrier, key);
}
```

For attribute setters, Java overload selection determines whether null reaches the setter.
An `Integer` passed for `AttributeKey<Long>` selects the primitive `int` overload and unboxes
before the setter runs, so keep its guard:

```java
Integer statusCode = response.getStatusCode();
if (statusCode != null) {
  attributes.put(HTTP_RESPONSE_STATUS_CODE, statusCode);
}
```

A boxed value matching the key uses the nullable generic overload:

```java
span.setAttribute(META_ENABLED, metadata.getEnabled());
```

Here `META_ENABLED` is an `AttributeKey<Boolean>`. A guard around
`view.getClass().getName()` protects a dereference, not just the setter.
