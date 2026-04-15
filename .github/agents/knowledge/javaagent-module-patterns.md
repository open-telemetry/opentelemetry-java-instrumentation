# [Javaagent] Module Structure Patterns

## Quick Reference

- Use when: reviewing `InstrumentationModule`, `TypeInstrumentation`, `VirtualField`, or `CallDepth` code
- Review focus: registration and naming, matcher performance, safe advice wiring

## InstrumentationModule

Every javaagent instrumentation module has a central `InstrumentationModule` subclass that
registers the instrumentation with the agent.

### Required structure

```java
@AutoService(InstrumentationModule.class)
public class MyLibrary10InstrumentationModule extends InstrumentationModule {

  public MyLibrary10InstrumentationModule() {
    super("my-library", "my-library-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new MyClassInstrumentation(), new MyOtherClassInstrumentation());
  }
}
```

### Rules

- Must have `@AutoService(InstrumentationModule.class)` — this registers it via SPI.
- Class name follows `{Library}{Version}InstrumentationModule` (e.g.,
  `OkHttp3InstrumentationModule`, `JedisInstrumentationModule`).
- Constructor `super()` arguments: the **first** (main) name must equal the Gradle module
  directory name excluding version suffix. Names use **kebab-case**. See
  [module-naming.md](module-naming.md) for the full naming convention.
- `typeInstrumentations()` returns the list of `TypeInstrumentation` implementations — use
  `Arrays.asList(...)` for multiple items and `Collections.singletonList(...)` for a single
  item.

### `classLoaderMatcher()` — Version-Boundary Detection

Override `classLoaderMatcher()` only when the instrumentation targets a specific library
version range and muzzle cannot distinguish that range on its own. Typical cases are classes
that were added, removed, renamed, or introduced by a library's own native OpenTelemetry
instrumentation, but are not referenced by the helper classes muzzle inspects.

**Execution order**: `classLoaderMatcher()` runs **first** and should be very cheap. It is
followed by `typeMatcher()`, then muzzle. Use `classLoaderMatcher()` to reject obvious
non-matches before the more expensive checks run.

**Override it when**:

- A landmark class was **added** in the target version and excludes older versions.
- A landmark class was **removed** in a newer version and excludes newer versions.
- A newer version ships **native OpenTelemetry instrumentation** and the agent should back off.

**Do not override it for**:

- Method signature changes.
- Parameter type changes.
- Field removals.
- Package relocations that muzzle already sees.

#### Pattern A: Single Landmark Class

This is the most common case: one class cleanly identifies the lower bound.

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  // class added in 8.0
  return hasClassesNamed("org.apache.wicket.request.RequestHandlerExecutor");
}
```

#### Pattern B: Multiple Landmark Classes

`hasClassesNamed()` with multiple arguments requires **all** listed classes to be present.
Use multiple classes only when one class is not enough.

- **Ambiguous floor**: one class was backported and no longer uniquely identifies the target
  version.
- **Floor + ceiling pinning**: one class proves "at least X", another proves "not yet Y".
- **Cross-JAR dependency**: the version boundary depends on classes from separate artifacts,
  such as a spec API plus a library implementation.

For multi-class checks, and for negated exclusions, add a version comment **above each class
name string**.

- **Positive floor class** in `hasClassesNamed(...)`: use `// added in X.Y`.
- **Positive ceiling class** in `hasClassesNamed(...)`: use `// removed in Y.Z`.
- **Negated exclusion class** in `not(hasClassesNamed(...))` or
  `.and(not(hasClassesNamed(...)))`: use `// added in Y.Z`, because that class's first
  appearance is what starts excluding `Y.Z+`.

You may add brief parenthetical context, but only when it adds interesting, non-duplicative
signal beyond the version role itself, for example native instrumentation notes, backport
context, or namespace rename notes.

One positive landmark class can sometimes provide **both** bounds: its presence proves the
module is at least version `X.Y`, and the same class disappears in `Y.Z`, so it also excludes
`Y.Z+`. In that case, use a combined comment such as `// added in X.Y, removed in Y.Z`.

When the entire `classLoaderMatcher()` return is a single `hasClassesNamed(...)` call with
one class — no `.and(...)` chaining — prefer the compact form with the version comment above
the statement or expression, instead of splitting the single string onto its own line:

```java
// added in 3.0
return hasClassesNamed("jakarta.faces.context.FacesContext");
```

```java
// added in 8.10 (native OTel support)
return not(hasClassesNamed("co.elastic.clients.transport.instrumentation.Instrumentation"));
```

The compact form does **not** apply when matchers are chained (e.g., positive + negated).
In that case, follow the multi-class rule: place each version comment directly above its
class name string.

**How to identify ceiling classes**: look for a **newer sibling module** for the same
library, such as `mongo-4.0` next to `mongo-3.7`. If the newer module's
`classLoaderMatcher()` checks a different variant of the same class, such as
`com.mongodb.internal.async.SingleResultCallback` instead of
`com.mongodb.async.SingleResultCallback`, the older variant was likely removed in the newer
version and can serve as the ceiling. A ceiling class may have been introduced much earlier
than the module's target version; the important fact is when it was **removed**, not when it
first appeared.

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  return hasClassesNamed(
      // added in 2.17.0 and backported to 2.12.3
      "org.apache.logging.log4j.core.lookup.ConfigurationStrSubstitutor",
      // added in 2.15.0
      "org.apache.logging.log4j.core.config.arbiters.DefaultArbiter");
}
```

Here `ConfigurationStrSubstitutor` alone would also match 2.12.3 because of the backport, so
`DefaultArbiter` is needed to tighten the match to 2.17+.

Another common shape is floor + ceiling pinning:

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  return hasClassesNamed(
      // added in 3.3.0.GA
      "org.hibernate.transaction.JBossTransactionManagerLookup",
      // removed in 4.0
      "org.hibernate.classic.Validatable");
}
```

Another example pins mongo 3.7 to 3.7.x–3.x:

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  return hasClassesNamed(
      // added in 3.7
      "com.mongodb.MongoClientSettings$Builder",
      // removed in 4.0 (replaced by com.mongodb.internal.async.SingleResultCallback)
      "com.mongodb.async.SingleResultCallback");
}
```

`SingleResultCallback` was introduced in driver 3.0 but **removed in 4.0**. Its presence keeps
the 3.7 module from activating on 4.0+ classloaders. That is why the comment must say
`// removed in 4.0`, not `// added in 3.0`.

#### Pattern C: Exclude Newer Versions with Native Instrumentation

Use `.and(not(hasClassesNamed(...)))` when the newer library version ships its own
OpenTelemetry instrumentation and the agent should opt out.

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  // added in 7.0.0
  return hasClassesNamed("org.elasticsearch.client.RestClient$InternalRequest")
      // added in 8.10 (native OTel support)
      .and(not(hasClassesNamed(
          "co.elastic.clients.transport.instrumentation.Instrumentation")));
}
```

#### Pattern D: Exclude an Upper Range

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  // added in azure-core 1.53
  return hasClassesNamed("com.azure.core.util.LibraryTelemetryOptions")
      // added in azure-core-tracing-opentelemetry 1.0.0-beta.47
      .and(not(hasClassesNamed("com.azure.core.tracing.opentelemetry.OpenTelemetryTracer")));
}
```

#### Rules for `classLoaderMatcher()`

- **Do NOT add `classLoaderMatcher()` for optimization.** The "skip when library is absent"
  optimization belongs on `TypeInstrumentation.classLoaderOptimization()`, not here.
  `classLoaderMatcher()` is only for **version-boundary detection**. Most modules do not need
  it.
- **Do NOT flag modules that omit `classLoaderMatcher()`.** The default (`any()`) is correct
  when muzzle can detect the version boundary on its own. Only flag a missing override when
  the module truly depends on an added or removed landmark class that muzzle does not inspect.
- **Version comments are required on landmark classes.** For multi-class checks, or whenever
  the landmark version differs from the module's base version, every `hasClassesNamed()` call
  needs a role comment. When the entire return expression is a single `hasClassesNamed(...)`
  call with one class (no `.and(...)` chaining), prefer the compact form with the version
  comment above the statement or expression. When matchers are chained, place each version
  comment directly above its class name string.
  - Positive floor class → `// added in X.Y`, optionally with brief, non-duplicative context
    such as `renamed from javax` or `backported to 2.12.3`.
  - Positive ceiling class → `// removed in Y.Z`, optionally with brief, non-duplicative
    context such as `replaced by jakarta variant` or `moved to internal.async`.
  - Negated exclusion class → `// added in Y.Z`, optionally with brief, non-duplicative
    context such as `native OTel support` or `shaded into core module`.
  - Single positive class serving as both floor and ceiling → include both boundaries, for
    example `// added in X.Y, removed in Y.Z`.
  Do not use `// added in` for a **positive** ceiling class, because the upper bound depends on
  when that class disappeared, not when it first appeared. Conversely, do use `// added in`
  for a **negated** exclusion class, because its first appearance is what starts the excluded
  range.
- **Single-class landmark comments are required.** When the entire return expression is a
  single `hasClassesNamed(...)` call with one class (no chaining), use the compact form with
  the version comment above the statement or expression. When matchers are chained (e.g.,
  positive `.and(not(...))` or multi-argument), place each version comment directly above its
  class name string — not above the outer `.and(` expression. Parenthetical context is fine
  only when it adds interesting, non-duplicative signal. If the same positive class also establishes the
  upper bound because it is removed or replaced in a newer sibling version, include that too,
  for example `// added in X.Y, removed in Y.Z`. First validate the stated boundaries from
  repository or upstream evidence; do not infer them from the module name alone.
- Prefer **one landmark class** per version boundary whenever possible.
- Pair with **muzzle config** (`assertInverse.set(true)`) for full coverage.
- Use `hasClassesNamed(...)` from `AgentElementMatchers`, not raw ByteBuddy matchers.
- `classLoaderMatcher()` runs against **every class loader** in the JVM before type matching
  begins, so it must stay fast. Use only `hasClassesNamed(...)`, which is a simple
  class-presence check. Never use hierarchy matchers such as `extendsClass(...)` or
  `implementsInterface(...)` here.

## TypeInstrumentation

Each `TypeInstrumentation` class instruments a single target class.

### Required structure

```java
class MyClassInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.example.library.MyClass");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(takesArguments(1)),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {
    // ... advice methods
  }
}
```

### `classLoaderOptimization()` — Skip When Library Absent

Override `classLoaderOptimization()` when the `typeMatcher()` uses expensive matchers
(e.g., `implementsInterface(...)`, `extendsClass(...)`, `isAnnotatedWith(...)`). This is
a fast pre-filter that runs before type matching: if a class from the target library is
absent, the expensive type matcher is skipped entirely.

```java
@Override
public ElementMatcher<ClassLoader> classLoaderOptimization() {
  return hasClassesNamed("com.example.library.TargetClass");
}
```

**This is the correct place for "skip when library is absent" optimization** — not
`InstrumentationModule.classLoaderMatcher()`. The module-level `classLoaderMatcher()` is
ANDed with `classLoaderOptimization()` at runtime, so the type-level check alone is
sufficient for optimization.

**When to override:**

- The `typeMatcher()` uses hierarchy matchers (`implementsInterface`, `extendsClass`) or
  annotation matchers — these require bytecode inspection of super classes/interfaces.
- The `typeMatcher()` uses `named(...)` or `namedOneOf(...)` — no override needed because
  name-only matchers are already fast (they check only the class name, no bytecode).

### Rules

- Do not flag or change the visibility or `final` modifier on advice classes.
- `typeMatcher()` should match only the types the instrumentation genuinely needs. Prefer
  `named("fully.qualified.ClassName")` or `namedOneOf(...)` for single classes.
  `extendsClass(...)` and `implementsInterface(...)` are appropriate when the instrumentation
  targets subclasses or implementors of a type.
- `transform()` wires method matchers to advice classes via `applyAdviceToMethod()`.
- `isMethod()` in method matchers inside `transform()` is redundant when the matcher
  already names a specific, non-empty method — e.g. `named("foo")` or `namedOneOf("foo", "bar")`.
  Keep `isMethod()` when the name could be empty, since `named("")` matches constructors and
  class initializers.
- Reference the advice class using `getClass().getName() + "$InnerClassName"` — not
  `this.getClass().getName() + "$InnerClassName"`, `InnerClassName.class.getName()`,
  `OuterClass.class.getName()`, or a string literal.
  Any `.class.getName()` reference — whether to the inner advice class or the outer
  instrumentation class — causes class loading in the agent's class loader, where library
  types used by the advice are unavailable (causing `NoClassDefFoundError`).
  `getClass().getName()` avoids this because it is a virtual call on the already-loaded
  instance, not a class literal. Omit the redundant `this.` qualifier and use the shorter
  repository convention.

## CallDepth (Preventing Recursive Instrumentation)

When an instrumented method may call itself through the instrumented library, use `CallDepth`
to prevent nested spans.

### Rules

- Use `CallDepth.forClass(TargetClass.class)` — the class argument identifies which
  instrumentation's depth is being tracked.
- In `@OnMethodEnter`: call `getAndIncrement()`, return the `CallDepth`.
- In `@OnMethodExit`: if `decrementAndGet() > 0`, skip span-ending logic (still nested).

## VirtualField (Attaching Context to Library Objects)

`VirtualField` attaches virtual fields to library classes without modifying their bytecode,
for associating OpenTelemetry context or state with library objects.

### Rules

- Call `VirtualField.find(Carrier.class, Value.class)` with class literals.
- **Inside `@Advice` methods** these calls are **rewritten at bytecode transformation time**
  by `VirtualFieldFindRewriter` into direct static calls to generated implementations —
  they are never executed at runtime. It is perfectly fine to call `VirtualField.find()`
  inside advice methods; do **not** extract them into helper classes or static final fields.
- **Outside advice** (helper classes, singletons, etc.) the call executes at runtime, so
  declare the result as a `static final` field to avoid repeated lookups.
- The first type parameter is the "carrier" class (the library object); the second is the
  attached value type.
- Uses weak-key semantics: when the carrier is garbage-collected, the value is released.
