# Java reflection and method handles

## Quick reference

- Load for changes involving `Method`, `MethodHandle`, `Constructor`, `Field`, reflective
  compatibility helpers, or lookups tied to a target package, module, or class loader.
- Prefer a direct Java call when the member is available across the supported library versions and
  ordinary Java access is sufficient.
- Resolve reflective members once per relevant runtime class and cache them when invocation repeats.
  Do not perform member lookup on an instrumentation hot path.
- Choose the invocation mechanism for its signature, access, class-loader, and failure semantics.
  Do not choose one only because a sibling helper uses it.

## Choose direct calls first

A direct call gives the compiler and muzzle the clearest view of the dependency. Use it when every
supported version has the member with a compatible signature and the code can access it normally.
Do not add reflection to avoid a compile-time dependency that the module already requires.

When compatibility or access requires runtime lookup, separate resolution from invocation. A
`static final` member works for one stable declaring class. Use a per-class cache such as
`ClassValue` when the runtime type can differ by library version or application class loader. Avoid
caching application classes or their members in a global agent-class-loader map that can retain the
application class loader.

One-time startup or configuration access does not need a more elaborate invocation layer. Repeated
invocation does need cached resolution, even when reflection is the clearer API.

## Choose the mechanism for the job

| Situation | Preferred approach |
| --- | --- |
| The member exists across supported versions and normal Java access works | Direct Java call |
| The exact runtime signature is known and typed invocation or `MethodType` adaptation is useful | Cached `MethodHandle` |
| Access requires a `MethodHandles.Lookup` associated with the target package, module, or class loader | `MethodHandle` resolved with that lookup |
| `CallSite` linking is part of the design | `MethodHandle` |
| Methods, constructors, getters, and setters benefit from one invocation abstraction | Cached `MethodHandle` |
| Startup, configuration, or another one-time operation needs a straightforward call, construction, or field access | `Method`, `Constructor`, or `Field` |
| Compatibility code discovers changing names, return types, or member shapes at runtime | `Method`, `Constructor`, or `Field` |
| An API already supplies reflection objects, or the code inspects annotations, modifiers, or generic metadata | Reflection object supplied by that API |
| The caller deliberately handles `InvocationTargetException` as a boundary around target failures | `Method` or `Constructor` |

These are reasons, not rankings. In particular, do not state or imply that `MethodHandle` is always
faster. Performance depends on lookup lifetime, handle shape, adaptation, JVM optimization, and the
call site. Require a concrete design reason or a measurement before making a performance claim.

## Keep access separate from invocation

First decide which code is legally allowed to access the member. Then decide how to invoke it. Use
the least-privileged lookup that works:

- `MethodHandles.publicLookup()` for public members exposed through public types.
- `MethodHandles.lookup()` when the helper's own package and module access are sufficient.
- A target-associated lookup, such as a lookup supplied through `@Advice.Origin`, when
  instrumentation must access a package-local or private target member.
- An existing class-loader-specific lookup when the target type lives in an instrumentation or
  application class loader that the agent lookup cannot see.

Do not call `setAccessible(true)` solely to convert a `Method`, `Constructor`, or `Field` into a
`MethodHandle`. That broadens access before conversion and can fail under module boundaries. Find
the member with an appropriate lookup, or keep the reflection object when it already expresses the
required access.

## Preserve failure behavior

`Method.invoke` wraps an exception thrown by the target method in
`InvocationTargetException`. `MethodHandle.invoke` and `invokeExact` propagate the target throwable
directly. They also report invocation-shape errors differently. Choose intentionally, and place
catching, unwrapping, logging, and fallback behavior around the failure form the caller should see.

In javaagent advice and helpers called by advice, lookup and invocation failures must not escape
into application code. Follow [Javaagent advice patterns](javaagent-advice-patterns.md) for
suppression and logging. Keep optional-member lookup failures distinct from failures thrown by a
member that was found. A missing optional compatibility hook may select a fallback, while a target
failure may need different handling.

## Treat consistency as a tiebreaker

Follow a sibling helper only when lifecycle, signatures, access, class-loader ownership, and failure
behavior are equivalent. Mixed use of reflection objects and method handles can be the right design.
Do not flag mixed usage or convert one form to the other solely for consistency.

## Repository examples

- `instrumentation/spring/spring-web/spring-web-3.1/library/src/main/java/io/opentelemetry/instrumentation/spring/web/v3_1/HeaderUtil.java`
  probes the exact `HttpHeaders` signatures used by supported Spring versions during class
  initialization. It caches nullable method handles and invokes them later. This is a good fit for
  known signatures with repeated calls.
- `instrumentation/couchbase/couchbase-2.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/couchbase/v2_0/CouchbaseConnectionStrings.java`
  discovers methods from runtime classes because Couchbase changed connection-string types and seed
  representations across versions. Its `ClassValue<Method>` caches preserve that dynamic discovery
  without forcing the shapes into one `MethodType`.
- `instrumentation/ktor/ktor-3.0/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/ktor/v3_0/ServerInstrumentation.java`
  receives a target-associated `MethodHandles.Lookup` from advice and uses it to read a private
  field whose name changed. The lookup context, rather than a general preference for handles, drives
  this choice. By contrast,
  `javaagent-tooling/src/main/java/io/opentelemetry/javaagent/tooling/instrumentation/indy/IndyBootstrap.java`
  uses class-loader-specific lookup, `MethodHandle.asType`, and mutable or constant call sites. That
  specialized linking code is not a template for ordinary compatibility helpers.
- `instrumentation/aws-sdk/aws-sdk-2.2/library/src/main/java/io/opentelemetry/instrumentation/awssdk/v2_2/internal/AwsJsonProtocolFactoryAccess.java`
  uses reflection once to discover and build optional AWS objects, then retains a bound method handle
  for repeated marshaller creation. The mixed approach matches the two different lifecycles.
