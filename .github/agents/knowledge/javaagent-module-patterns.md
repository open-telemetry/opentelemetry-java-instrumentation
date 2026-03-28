# [Javaagent] Module Structure Patterns

## Quick Reference

- Use when: reviewing `InstrumentationModule`, `TypeInstrumentation`, `Singletons`, `VirtualField`, or `CallDepth` code
- Review focus: registration and naming, matcher performance, safe advice wiring, singleton and hot-path patterns

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

Override `classLoaderMatcher()` when the instrumentation targets a specific library version
range and muzzle alone cannot distinguish it (e.g., a class was added or removed between
versions but isn't referenced by helper classes).

**Execution order**: `classLoaderMatcher()` runs **first** (fast), then `typeMatcher()`, then
muzzle (expensive, cached). Use `classLoaderMatcher()` to pre-filter before muzzle runs.

**When to override** (otherwise let muzzle handle it):

- A landmark class was **added** in the target version (exclude older versions).
- A landmark class was **removed** in a newer version (exclude newer versions).
- A newer version has **native OpenTelemetry instrumentation** — avoid double-instrumentation.

**When NOT to override** (muzzle catches these automatically):

- Method signature changes, parameter type changes, field removals, package relocations.

#### Pattern A: Check for a class added in the target version (most common)

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  // class added in 8.0
  return hasClassesNamed("org.apache.wicket.request.RequestHandlerExecutor");
}
```

#### Pattern B: Multiple landmark classes (AND logic)

`hasClassesNamed()` with multiple arguments requires **ALL** classes to be present. A single
class suffices for a simple version boundary — use multiple classes only when:

- **One class is ambiguous** — e.g., it was backported to an older branch, so it doesn't
  uniquely identify the target version. Adding a second class narrows the match.
- **Floor + ceiling pinning** — one class proves "at least version X", another proves
  "not yet version Y" (e.g., Hibernate 3.3 checks a class absent before 3.3 AND a class
  absent in 4.0+, pinning to exactly 3.3.x–3.x).
- **Cross-JAR dependency** — the classes come from different JARs that may be independently
  present (e.g., Jersey checks both the JAX-RS spec API and the Jersey implementation class;
  AWS SDK Lambda checks both the Lambda module and JSON protocol core).

Place a comment **above each class name string** stating its version role:

- **Floor class** (proves "at least version X"): use `// added in X.Y`.
- **Ceiling class** (proves "not yet version Y"): use `// removed in Y.Z`.

**How to identify ceiling classes**: check whether a **newer sibling module** exists for
the same library (e.g., `mongo-4.0` next to `mongo-3.7`). If the newer module's
`classLoaderMatcher()` checks a different variant of the same class (e.g.,
`com.mongodb.internal.async.SingleResultCallback` vs `com.mongodb.async.SingleResultCallback`),
the old variant was likely removed in the newer version and serves as a ceiling. A ceiling
class may have been *introduced* much earlier than the module's target version — the relevant
fact is when it was **removed**, not when it was added.

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

Here `ConfigurationStrSubstitutor` alone would also match 2.12.3 (due to back-port),
so `DefaultArbiter` (added in 2.15) is required to tighten the match to 2.17+.

Another common shape — floor + ceiling:

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

Another floor + ceiling example — mongo 3.7 pins to 3.7.x–3.x:

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  return hasClassesNamed(
      // added in 3.7
      "com.mongodb.MongoClientSettings$Builder",
      // removed in 4.0 — excludes driver 4.0+ where the 4.0 module takes over
      "com.mongodb.async.SingleResultCallback");
}
```

Here `SingleResultCallback` was introduced in driver 3.0 but **removed in 4.0**. Its
presence ensures the 3.7 module does not activate on 4.0+ classloaders. The comment must
say `// removed in 4.0`, not `// added in 3.0` — the purpose is the upper bound.

#### Pattern C: Exclude newer versions with native instrumentation

Use `.and(not(hasClassesNamed(...)))` to opt out when the library ships its own
OpenTelemetry instrumentation:

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  // InternalRequest was introduced in 7.0.0
  return hasClassesNamed("org.elasticsearch.client.RestClient$InternalRequest")
      // Instrumentation class was introduced in 8.10 (native OTel support)
      .and(not(hasClassesNamed(
          "co.elastic.clients.transport.instrumentation.Instrumentation")));
}
```

#### Pattern D: Exclude a version range (upper bound)

```java
@Override
public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
  // LibraryTelemetryOptions was introduced in azure-core 1.53
  return hasClassesNamed("com.azure.core.util.LibraryTelemetryOptions")
      // OpenTelemetryTracer was introduced in azure-core-tracing-opentelemetry 1.0.0-beta.47
      .and(not(hasClassesNamed("com.azure.core.tracing.opentelemetry.OpenTelemetryTracer")));
}
```

#### Rules for `classLoaderMatcher()`

- **Do NOT add `classLoaderMatcher()` for optimization.** The "skip when library is absent"
  optimization belongs on `TypeInstrumentation.classLoaderOptimization()`, not here.
  `classLoaderMatcher()` is only for **version-boundary detection** — cases where muzzle
  alone cannot distinguish the target version range. Most modules do not need it.
- **Do NOT flag modules that lack a `classLoaderMatcher()` override.** The default (`any()`)
  is correct when muzzle can detect version boundaries on its own (the common case). Only
  flag a *missing* override when the module targets a specific version range and the
  versioning signal (added/removed landmark class) is not part of the classes muzzle inspects.
- **Version comments on landmark classes.** For multi-class checks, `.and(not(...))`
  chains, or cases where the landmark version differs from the module's base version,
  each `hasClassesNamed()` call must have a `//` comment stating the class's **role**:
  - Floor class → `// added in X.Y` (class introduced in version X.Y).
  - Ceiling class → `// removed in Y.Z` (class removed in version Y.Z, ensuring the
    module does not activate on Y.Z+).
  Do not use `// added in` for a ceiling class — that states when the class first appeared,
  which is irrelevant and misleading; the purpose is the upper bound.
  To identify ceiling classes, check if a newer sibling module's `classLoaderMatcher()`
  checks a different variant or replacement class.
- **Single-class lower-bound comments are required.** When a single-class
  `hasClassesNamed(...)` check exists solely to establish the module's lower bound, add an
  inline `// added in X.Y` comment. The comment explains why the matcher exists and which
  version boundary it enforces. First validate that `X.Y` is factually correct from
  repository or upstream evidence; do not infer it from the module name alone. Flag a
  missing comment in this case, and do not flag an existing one for removal unless the
  comment is demonstrably wrong.
- Prefer **one landmark class** per version boundary — choose the most stable/specific class.
- Pair with **muzzle config** (`assertInverse.set(true)`) for full coverage.
- Use `hasClassesNamed(...)` (from `AgentElementMatchers`) — not raw ByteBuddy matchers.
- `classLoaderMatcher()` runs against **every class loader** in the JVM before type matching
  begins. It must be fast: use only `hasClassesNamed(...)` (a simple class-presence check).
  Never use hierarchy matchers (`extendsClass(...)`, `implementsInterface(...)`) here.

## TypeInstrumentation

Each `TypeInstrumentation` class instruments a single target class.

### Required structure

```java
public class MyClassInstrumentation implements TypeInstrumentation {

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

- Do not flag or change the visibility or `final` modifier on `TypeInstrumentation`,
  `InstrumentationModule`, or advice classes. Both `public class` and package-private `class`
  (with or without `final`) are acceptable — this is not a style issue in javaagent code.
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
  `InnerClassName.class.getName()`, `OuterClass.class.getName()`, or a string literal.
  Any `.class.getName()` reference — whether to the inner advice class or the outer
  instrumentation class — causes class loading in the agent's class loader, where library
  types used by the advice are unavailable (causing `NoClassDefFoundError`).
  `getClass().getName()` avoids this because it is a virtual call on the already-loaded
  `this` instance, not a class literal.

## Singletons Pattern

Javaagent modules hold their `Instrumenter` instances and shared resources in a dedicated
`Singletons` holder class (e.g., `MyLibrarySingletons`).

### Rules

- `Instrumenter` instances must be initialized at class-load time — either as a `static final`
  field initializer or in a `static {}` block.
- Use `GlobalOpenTelemetry.get()` to obtain the `OpenTelemetry` instance.
- The instrumentation name string (second argument to `builder()`) should match the Gradle
  module path: `"io.opentelemetry.<module-name>"` (e.g., `"io.opentelemetry.jedis-4.0"`).
- Provide a static accessor method (typically named `instrumenter()`).

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
