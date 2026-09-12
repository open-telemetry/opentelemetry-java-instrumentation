# Java reflection and method handles

## Prefer direct access

Prefer a direct Java call when supported library versions expose a compatible member and ordinary
Java access works. This gives the compiler and muzzle a clear view of the dependency. Use reflection
only when compatibility, access, or runtime discovery requires it.

Before treating a lookup as runtime-class-dependent, identify the member's actual declaring type.
A receiver typed as `Object`, or existing code that calls `receiver.getClass()`, does not prove that
each implementation needs a separate lookup. Check whether a stable interface or base class
declares the member. A `Method` obtained from that type can invoke compatible implementations.

## Cache repeated lookup

Flag production Java code that repeats the same reflective method lookup on a path that may execute
more than once. Resolve the method once and cache the resulting `Method` or `MethodHandle`. Use a
`static final` field when the declaring class is fixed.

When a fixed declaring type is optional or unavailable at compile time, load it once by name without
initializing it and cache the nullable method in a `static final` field. In javaagent code, first
verify the helper-loading strategy. An injected helper or isolated instrumentation-module helper is
scoped to the instrumented class loader and can load an application type through its defining class
loader. A bootstrap or otherwise shared helper cannot assume that one application class is valid
for every caller.

When the lookup depends on the runtime class, use `ClassValue` instead of a static map keyed by
`Class<?>`. Static maps can keep application classloaders alive. Cache missing methods too when
supported library versions may not provide the method.

Do not apply this rule to test code or a lookup that is provably executed only once during
initialization.

## Choose the mechanism for the job

| Situation | Preferred approach |
| --- | --- |
| Supported versions expose a compatible member and normal access works | Direct Java call |
| A fixed declaring type is optional or unavailable at compile time | Static cached `Method` or `MethodHandle` resolved by class name |
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
