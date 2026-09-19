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

Use this checklist before choosing a cache:

1. Find the class or interface that declares the required member. Do not start with the receiver's
   declared Java type or concrete runtime class.
2. Check whether lookup selects one fixed optional class or one stable interface for the detected
   library version. If it does, cache one nullable `static final Method` or `MethodHandle`.
3. Use `ClassValue` only when the declaring member, signature, or required access lookup truly
   changes with the concrete runtime class.

An exact class-name check strongly indicates a fixed declaring type. Code that accepts `Object`,
checks for `io.netty.channel.unix.DomainSocketAddress`, and calls `path()` does not need a cache
entry for every receiver class.

### Fixed optional declaring class

Do not key the lookup by `address.getClass()` when the code only supports one known optional class:

```java
return pathMethods.get(address.getClass()).invoke(address);
```

Load the optional class once without initializing it, then cache its method:

```java
private static final @Nullable Method DOMAIN_SOCKET_PATH = findDomainSocketPath();

private static @Nullable Method findDomainSocketPath() {
  try {
    Class<?> type =
        Class.forName(
            "io.netty.channel.unix.DomainSocketAddress",
            false,
            InjectedAddressHelper.class.getClassLoader());
    return type.getMethod("path");
  } catch (ReflectiveOperationException | LinkageError ignored) {
    return null;
  }
}

Method path = DOMAIN_SOCKET_PATH;
return path == null ? null : path.invoke(address);
```

This cache belongs in a helper whose defining class loader can see the optional class.

### Stable or version-selected interface

Do not resolve `Connection.unwrap()` separately for every implementation:

```java
Method unwrap = unwrapMethods.get(connection.getClass());
return unwrap.invoke(connection);
```

Resolve the supported interface once. The interface method can invoke any compatible
implementation:

```java
private static final @Nullable Method CONNECTION_UNWRAP =
    findSupportedConnectionInterfaceMethod("unwrap");

Method unwrap = CONNECTION_UNWRAP;
return unwrap == null ? null : unwrap.invoke(connection);
```

This also applies when version detection chooses between a small number of known interface names.
Version selection changes which fixed method the helper stores, not whether the method depends on
each receiver's concrete class.

### Runtime-dependent declaring class

Use `ClassValue` when unrelated runtime-generated classes each declare the required method and no
shared type declares it:

```java
private static final ClassValue<Method> GENERATED_VALUE_METHODS =
    new ClassValue<Method>() {
      @Override
      protected Method computeValue(Class<?> generatedClass) {
        try {
          return generatedClass.getDeclaredMethod("generatedValue");
        } catch (NoSuchMethodException e) {
          throw new IllegalStateException(e);
        }
      }
    };

return GENERATED_VALUE_METHODS.get(receiver.getClass()).invoke(receiver);
```

Here the required `Method` differs by concrete generated class, so one static `Method` cannot
represent every lookup.

## Cache repeated lookup

Flag production Java code that repeats the same reflective method lookup on a path that may execute
more than once. Resolve the method once and cache the resulting `Method` or `MethodHandle`. Use a
`static final` field when the declaring class is fixed.

When a fixed declaring type is optional or unavailable at compile time, load it once by name without
initializing it and cache the nullable method in a `static final` field.

When the lookup depends on the runtime class, use `ClassValue` instead of a static map keyed by
`Class<?>`. Static maps can keep application classloaders alive. Cache missing methods too when
supported library versions may not provide the method.

Do not apply this rule to test code or a lookup that is provably executed only once during
initialization.

## Respect class loader boundaries

An injected helper's static cache is scoped to its defining application class loader. Separate
applications receive separate helper classes and caches, so the helper can cache the optional type
visible to that loader.

A bootstrap or otherwise shared helper has one cache across application class loaders. It must not
cache one application's `Class`, `Method`, or `MethodHandle` in a global static field. Keep the
cache in the injected helper that can name the application type, or pass a loader-scoped accessor
to the shared code.

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
