---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Java reflection and method handles

Apply these rules to reflection-based compatibility code.

- Choose lookup and cache scope from the declaring type, supported signatures, class loaders,
  access, and failure behavior.

## Prefer direct access

- Prefer a direct Java call when supported library versions expose a compatible member and ordinary
  Java access works. This gives the compiler and Muzzle a clear view of the dependency. Use reflection
  only when compatibility, access, or runtime discovery requires it.

## Cache repeated lookup by declaring type

- For production Java code that repeats the same reflective method lookup on a path that may execute
  more than once, cache the resulting `Method` or `MethodHandle` according to the actual declaring
  type. Use one `static final` field for a fixed declaring type, including a version-selected
  interface. A method from an interface or base class works for compatible implementations.
  If the fixed type is optional or unavailable at compile time, load it once by name without
  initialization, for example with `Class.forName(name, false, helperClassLoader)`. Cache missing
  methods too when supported versions may not provide them, using a nullable field for a fixed type.
  When the required metadata varies by concrete runtime class, such as for unrelated generated
  classes that each declare the member, use `ClassValue` instead of a static map keyed by `Class<?>`,
  which can keep application class loaders alive. Neither an `Object` receiver nor an existing
  `getClass()` or `ClassValue` proves that lookup depends on the runtime class. For example,
  `DomainSocketAddress.path()` and `unwrap()` on a version-selected `Connection` interface each
  use a fixed declaring type and do not need per-class entries. This caching rule does not apply to
  test code or lookups that provably run once during initialization.

- Keep an application-specific static cache in a helper whose defining class loader is isolated for
  that application. A bootstrap or otherwise shared helper must not cache one application's `Class`,
  `Method`, or `MethodHandle` globally. Pass a loader-scoped accessor to shared code when needed.

## Choose the mechanism for the job

- Choose reflection or a method handle based on access and calling requirements. Do not choose
  `MethodHandle` solely for an assumed performance advantage.

  | Situation | Preferred approach |
  | --- | --- |
  | A method handle is needed and the supported signatures are known | Cached `MethodHandle` using `findStatic` or `findVirtual`; try the known version-specific alternatives |
  | Access requires a target-associated lookup, or `CallSite` linking is part of the design | `MethodHandle` |
  | Setup performs a one-time call, construction, or field access | `Method`, `Constructor`, or `Field` |
  | The supported method signatures aren't known ahead of time | Reflective discovery using `Method`, `Constructor`, or `Field` |
  | Code receives reflection objects or inspects member metadata | `Method`, `Constructor`, or `Field` |
  | The caller deliberately handles `InvocationTargetException` | `Method` or `Constructor` |

- Mixed use of reflection objects and method handles does not itself require
  conversion.

### Choose the lookup scope

- For compatibility code that needs a method handle, use `findStatic` or `findVirtual` when
  the supported signatures are known. Enumerate known version-specific alternatives rather than using
  reflection to remove compatibility branches. Resolve non-JDK signature types without initialization
  through the method's declaring-class loader, for example
  `Class.forName(name, false, declaringClass.getClassLoader())`, rather than the helper's loader.

- Use reflective discovery only when the supported method signatures aren't known ahead of time or the
  code needs reflective metadata. On HotSpot, `getMethod` obtains declared public methods on each class it
  searches; `getDeclaredMethod` obtains all declared methods of the class. Constructing those `Method`
  objects resolves their parameter, return, and declared exception types. A missing dependency in an
  unrelated method can therefore break the lookup with `NoClassDefFoundError`. Exact method-handle
  lookup avoids this broad enumeration.

- If a `Method` is already available and a method handle is needed, use `lookup.unreflect(method)`
  instead of reconstructing its signature to look up the same method again. Derive follow-on
  accessors from `method.getReturnType()` rather than loading that type again by name.

## Separate access from invocation

- First determine how the code may access the member. Then choose how to invoke it. Use a
  target-associated or class-loader-specific lookup when package, module, or class-loader boundaries
  require one. Use the least-privileged lookup that works.

- Do not call `setAccessible(true)` only to convert a reflection object into a `MethodHandle`. Resolve
  the handle with an appropriate lookup, or keep the reflection object when it already provides the
  required access.

## Preserve failure behavior

- `Method.invoke` wraps a target exception in `InvocationTargetException`.
  `MethodHandle.invoke` and `invokeExact` propagate the target throwable directly. Choose the form
  that matches the caller's catching, unwrapping, logging, and fallback behavior.

- Lookup and invocation failures in javaagent advice or helpers called by advice must not escape into
  application code. Executable advice with fallible bodies suppresses unexpected failures; helpers
  log them where they occur unless they are expected optional probes.
