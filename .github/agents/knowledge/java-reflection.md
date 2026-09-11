# Java reflection and method handles

## Quick reference

- Prefer a direct Java call when supported library versions expose a compatible member and ordinary
  Java access works. This gives the compiler and muzzle a clear view of the dependency.
- Use reflection only when compatibility, access, or runtime discovery requires it.
- Cache reflective lookup when calls repeat. Never perform member lookup on an instrumentation hot
  path.
- Choose between reflection objects and method handles based on the required signature, access,
  invocation style, and failure behavior.

## Choose the mechanism for the job

| Situation | Preferred approach |
| --- | --- |
| Supported versions expose a compatible member and normal access works | Direct Java call |
| The exact signature is known and typed or adapted invocation helps, or several member kinds need one invocation abstraction | Cached `MethodHandle` |
| Access requires a target-associated lookup, or `CallSite` linking is part of the design | `MethodHandle` |
| Setup performs a one-time call, construction, or field access | `Method`, `Constructor`, or `Field` |
| Compatibility code discovers changing API shapes, receives reflection objects, or inspects member metadata | `Method`, `Constructor`, or `Field` |
| The caller deliberately handles `InvocationTargetException` | `Method` or `Constructor` |

Do not choose or recommend `MethodHandle` solely for an assumed performance
advantage. Use the design criteria above instead.

When both approaches fit equally well, follow nearby precedent. Do not flag or
convert mixed usage solely for consistency.

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
and logging.
