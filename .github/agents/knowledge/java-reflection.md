# Java reflection and method handles

## Prefer direct access

Prefer a direct Java call when supported library versions expose a compatible member and ordinary
Java access works. This gives the compiler and muzzle a clear view of the dependency. Use reflection
only when compatibility, access, or runtime discovery requires it.

Before treating a lookup as runtime-class-dependent, identify the member's actual declaring type.
A receiver typed as `Object`, or existing code that calls `receiver.getClass()`, does not prove that
each implementation needs a separate lookup. An existing `ClassValue` does not prove it either.
A `Method` obtained from an interface or base class can invoke compatible implementations.

## Decide from the declaring type

Choose a cache with this checklist:

1. Identify the class or interface that declares the member.
2. For one fixed optional class or one version-selected stable interface, cache one nullable
   `static final Method` or `MethodHandle`.
3. Use `ClassValue` only when the required metadata truly varies by concrete runtime class.

For a fixed optional `DomainSocketAddress.path()`, avoid
`pathMethods.get(address.getClass())`. An exact class-name check strongly indicates one declaring
type. Load that class once without initialization and cache its method:

```java
private static final @Nullable Method DOMAIN_SOCKET_PATH =
    findMethod("io.netty.channel.unix.DomainSocketAddress", "path");
```

Likewise, avoid `unwrapMethods.get(connection.getClass())` when a stable `Connection` interface
declares `unwrap()`. Resolve the interface selected for the detected library version once:

```java
private static final @Nullable Method CONNECTION_UNWRAP =
    findVersionedInterfaceMethod("Connection", "unwrap");
```

An interface `Method` can invoke compatible implementations; their concrete classes do not require
separate entries.

In contrast, use `ClassValue` when unrelated generated classes each declare the required method and
no shared type declares it:

```java
private static final ClassValue<Method> GENERATED_VALUE_METHOD =
    new ClassValue<Method>() {
      @Override
      protected Method computeValue(Class<?> type) {
        return findGeneratedValueMethod(type);
      }
    };

return GENERATED_VALUE_METHOD.get(receiver.getClass()).invoke(receiver);
```

## Cache repeated lookup

Flag production Java code that repeats the same reflective method lookup on a path that may execute
more than once. Resolve the method once and cache the resulting `Method` or `MethodHandle`. Use a
`static final` field when the declaring class is fixed.

When a fixed declaring type is optional or unavailable at compile time, load it once by name without
initializing it, for example with `Class.forName(name, false, helperClassLoader)`, and cache the
nullable method in a `static final` field.

When the lookup depends on the runtime class, use `ClassValue` instead of a static map keyed by
`Class<?>`. Static maps can keep application classloaders alive. Cache missing methods too when
supported library versions may not provide the method.

Do not apply this rule to test code or a lookup that is provably executed only once during
initialization.

An injected helper's static cache is scoped to its defining application class loader. A bootstrap
or shared helper must not cache one application's `Class`, `Method`, or `MethodHandle` globally.
Keep that cache in the injected helper, or pass a loader-scoped accessor to shared code.

## Choose the mechanism for the job

| Situation | Preferred approach |
| --- | --- |
| Supported versions expose a compatible member and normal access works | Direct Java call |
| A fixed optional class or version-selected stable interface declares the member | One nullable static cached `Method` or `MethodHandle` |
| The declaring type or required method metadata depends on the concrete runtime class | `ClassValue` containing the cached accessor |
| The exact signature is known and typed or adapted invocation helps, or several member kinds need one invocation abstraction | Cached `MethodHandle` |
| Access requires a target-associated lookup, or `CallSite` linking is part of the design | `MethodHandle` |
| Setup performs a one-time call, construction, or field access | `Method`, `Constructor`, or `Field` |
| Compatibility code discovers changing API shapes, receives reflection objects, or inspects member metadata | `Method`, `Constructor`, or `Field` |
| The caller deliberately handles `InvocationTargetException` | `Method` or `Constructor` |

Do not choose or recommend `MethodHandle` solely for an assumed performance
advantage. Use the design criteria above instead.

Mixed use of reflection objects and method handles is not by itself a reason to
flag or convert the code.

## Separate access from invocation

First determine how the code may access the member. Then choose how to invoke it. Use a
target-associated or class-loader-specific lookup when package, module, or class-loader boundaries
require one. Use the least-privileged lookup that works.

Do not call `setAccessible(true)` only to convert a reflection object into a `MethodHandle`. Resolve
the handle with an appropriate lookup, or keep the reflection object when it already provides the
required access.

## Preserve failure behavior

`Method.invoke` wraps a target exception in `InvocationTargetException`.
`MethodHandle.invoke` and `invokeExact` propagate the target throwable directly. Choose the form
that matches the caller's catching, unwrapping, logging, and fallback behavior.

Lookup and invocation failures in javaagent advice or helpers called by advice must not escape into
application code. Follow [Javaagent advice patterns](javaagent-advice-patterns.md) for suppression
and [best-effort suppressed failures](general-rules.md#javaagent-best-effort-suppressed-failures)
for logging.
