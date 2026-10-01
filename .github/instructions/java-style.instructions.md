---
applyTo: "**/*.java"
---

# Java review conventions

Apply only to changed Java code. Do not comment on formatting, compilation,
Checkstyle, ErrorProne, or NullAway failures already covered by CI. Confirm
the stated exception before reporting a repository convention.

- **Visibility**: use the narrowest access that works. Add `// visible for
  testing` if a production member has broader visibility solely for direct
  test access. Members referenced directly by advice must remain public because
  the transformer may inline the advice even when its source annotation sets
  `inline = false`; transitive helper members may use narrower visibility. A
  single public class in a module may be needed for Javadoc.
- **Public API**: make public API classes `final` where possible; name
  public getters `get*` or `is*` for booleans. Javaagent singleton accessors
  directly returning stored fields are an exception to getter naming. Public
  implementation classes that are not API belong in an `.internal` package.
- **Fields**: use `SCREAMING_SNAKE_CASE` for immutable value constants and
  semantic keys/handles, not just because a field is `static final`. A
  `static final VirtualField` *must* be uppercase regardless of visibility
  or runtime initialization; this is not mandatory for `MethodHandle` or
  `Pattern`. Loggers, instrumenters, helpers, and caches stay lower camel
  case. Collections exposed outside a class must be unmodifiable;
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
  null value. A guard around a directly passed boxed value is redundant,
  except when an `Integer` passed for `AttributeKey<Long>` selects the
  `int` overload and would unbox null. Retain a guard protecting a
  dereference or derived computation.
- **Reflection**: cache reflective method lookups that may repeat on a
  production path. For a fixed declaring class or stable interface,
  resolve once; use `ClassValue` only when lookup metadata actually varies
  by concrete runtime class. Do not keep application-loader classes or
  members in a global bootstrap cache. Test and one-time initialization
  lookups do not require caching. `Method.invoke` wraps target exceptions
  while `MethodHandle.invoke` does not; preserve failure handling when
  changing invocation.
- **Regular expressions**: precompile literal Java regexes when a
  production call may repeatedly compile them. `String.split` has a fast
  path for simple separators such as `","` and `"\\."`; do not ask for
  a `Pattern` there, in test code, or for one-time initialization.
- **Lambda allocation**: do not ask to hoist a non-capturing lambda or
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
- **Other conventions**: do not add `final` to parameters or locals;
  prefer `value == null` to `null == value`; do not flip
  `value.equals(CONSTANT)` solely for speculative null safety.
  Avoid `Optional` in public API signatures and hot-path code. Omit
  explicit type arguments on generic method calls when type inference
  already works; keep them where compilation actually requires them.
