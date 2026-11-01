# [Build] Gradle Conventions

Consult this article when changing module registration, Muzzle ranges,
dependencies, or test-task wiring. It includes the build-specific exceptions
and steps for finding version siblings and configuring task variants.

## `settings.gradle.kts` Ordering

New `include(...)` entries must be in **alphabetical order** within the surrounding group.

## Muzzle Configuration

Javaagent modules must have a `muzzle` block in `build.gradle.kts`. Each `pass` block must
include `group.set()`, `module.set()`, `versions.set()` (proper Maven range like `"[1.0,)"`).

Include `assertInverse.set(true)` **unless** the version range covers all versions (e.g.
`"(,)"` or `"[0,)"`) — in that case there is no meaningful inverse to assert.

When adding `assertInverse`, insert it immediately after `versions` (or after `skip` if
`skip` is present). Do not reorder other existing properties.

```kotlin
muzzle {
  pass {
    group.set("com.example")
    module.set("example-lib")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
}
```

Use `fail` blocks for versions that must NOT be instrumented. Use `skip()` for specific
broken/incompatible versions.

### Compatibility range ownership

Versioned javaagent Gradle projects for the same component and target artifact should ideally own
disjoint compatibility ranges. Create a new versioned project when the baseline implementation
changes incompatibly and the old and new implementations are mutually exclusive, or when a real
build or dependency boundary prevents one project from compiling both implementations.

When behavior is additive, optional, or starts in a higher subrange while the baseline remains
compatible, keep the independently selected `InstrumentationModule` classes in the existing
javaagent project. Public enablement names describe the library and independently selectable
features, not compatibility implementations. Multiple classes may share all their public names.
Configure one Muzzle `pass` per range and target artifact, excluding unrelated modules in each pass.
Prefer `excludeInstrumentationName(...)` when the public name selects the intended classes. Use
`excludeInstrumentationModule(...)` with a fully qualified
class name when public names cannot distinguish the required implementations. Both forms apply
to inverse checks, and unknown class exclusions fail the check. Keep the baseline library dependency
and add newer or optional referenced types with `compileOnly` when appropriate.

Multiple Scala or artifact-name variants, a separate enablement name, a different test matrix, or
dependencies on separate library, testing, helper, generated-code, or language-specific projects do
not by themselves require another javaagent project. Those auxiliary projects may remain separate,
but they should not own an overlapping `InstrumentationModule` unless the javaagent selector cannot
share the build.

Narrow exceptions include incompatible toolchains or plugins and version-specific generated or
shaded compile classpaths. The `opentelemetry-api-*` family is one example: each layer compiles
against a distinct shaded API configuration. Kotlin helpers such as Kotlin Flow can share a
javaagent project with Java `InstrumentationModule` classes when `byteBuddyKotlin` is disabled.
Muzzle generation runs through `byteBuddyJava`, whose classpath includes the Kotlin compiler output,
and recursively inspects the referenced Kotlin helpers.

Muzzle verifies generated symbol references, not whether every Byte Buddy method matcher matches.
A pass lower bound may therefore start when the referenced types exist even when a more precise
runtime matcher activates the behavior only in a later version.

### Important: do not remove existing `assertInverse` or `skip`

- Never remove an existing `assertInverse.set(true)` from a `pass` block.
- Never remove existing `skip()` entries — they apply to both the `pass` range and the
  auto-generated `assertInverse` check. Removing them causes the inverse to test broken
  versions that will fail.

## Gradle Plugin Verification

Verify `build.gradle.kts` applies the correct plugin for the module type:

| Module type  | Required plugin                  |
| ------------ | -------------------------------- |
| `javaagent/` | `otel.javaagent-instrumentation` |
| `library/`   | `otel.library-instrumentation`   |
| `testing/`   | `otel.java-conventions`          |

## Shared Gradle Project Properties

In `build.gradle.kts`, prefer the shared `otelProps` extension for repository-wide Gradle project
properties that are already modeled there, including:

- `testLatestDeps`
- `denyUnsafe`
- `collectMetadata`
- `testJavaVersion`
- `testJavaVM`
- `maxTestRetries`
- `enableStrictContext`

Example:

```kotlin
val springBootVersion = if (otelProps.testLatestDeps) "2.+" else "2.6.15"
```

For module-local one-off properties that are not part of `otelProps`, using `findProperty(...)`
directly is still fine.

`settings.gradle.kts` is the exception: it cannot use project extensions like `otelProps`, so
direct `gradle.startParameter.projectProperties[...]` access is expected there.

### `testLatestDeps` Test JVM Property

Do not add `systemProperty("testLatestDeps", otelProps.testLatestDeps)` solely because a module
has `latestDepTestLibrary(...)` declarations or sibling modules set the property. Add it only when
the module's test JVM actually needs to read the flag, for example when an in-scope test source or
shared testing source used by that module calls `TestLatestDeps.testLatestDeps()` / `testLatestDeps()`
or checks the `testLatestDeps` system property directly.

`latestDepTestLibrary(...)` already affects Gradle dependency resolution when
`-PtestLatestDeps=true` is set; the system property is only for runtime test code that branches on
that mode.

### Multiple library versions in javaagent integration tests

`library("group:artifact:version")` contributes the baseline library to `compileOnly` and the
default test suite. `testLibrary("group:artifact:version")` also contributes to
`testImplementation`; it is appropriate when the default tests should compile or run against a
different version, or need a test-only artifact.

Do not use `library(...)` plus `testLibrary(...)` for the same module coordinate when the goal is
to test two runtime versions. Gradle resolves one dependency graph for the default test suite and
normally selects the higher requested version, so the default javaagent integration tests no
longer exercise the baseline runtime.

When both versions need real javaagent integration coverage:

- Keep the baseline dependency in `library(...)` and leave baseline-compatible tests in `src/test`.
- Move only tests that require the newer API or runtime behavior into a version-specific source
  set, such as `src/version36Test`.
- Register a `JvmTestSuite` for that source set and declare the newer library inside the suite.
- Wire `testing.suites` into `check` so the additional suite cannot be skipped.

```kotlin
testing {
  suites {
    register<JvmTestSuite>("version36Test") {
      dependencies {
        implementation("group:artifact:3.6.1")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
```

Keep `testLibrary(...)` for the single-runtime or test-only-artifact cases it is designed for.

## `testInstrumentation` Dependencies

The `testInstrumentation` configuration declares which other javaagent instrumentation modules
should be active in the test agent.

### When to use

- **Sibling version modules**: list all other versions in the same library family (e.g.,
  `netty-4.0` lists `netty-3.8` and `netty-4.1`). This ensures the agent loads all of them
  and muzzle selects only the correct one, preventing double-instrumentation.
- **Internal library dependencies**: if the library under test uses another instrumented
  library internally (e.g., gRPC uses Netty, AWS SDK uses Apache HTTP), list those javaagent
  modules so expected spans are produced during tests.

### Rules

- Only reference `:javaagent` leaf modules.
- **Sibling cross-version references are required**: every javaagent module in a version
  family must list all other sibling `:javaagent` modules via `testInstrumentation`.

### Exception: modules already bundled into `agent-for-testing`

A small set of javaagent modules are bundled directly into the main agent via
`baseJavaagentLibs(...)` in `javaagent/build.gradle.kts`, and therefore into
`agent-for-testing` as well. For these, the sibling cross-version rule does **not** apply:
they are already loaded in every test JVM, so adding them via `testInstrumentation`
from a sibling's `build.gradle.kts` is redundant.

In particular, do not add `testInstrumentation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.N:javaagent"))`
entries to sibling `opentelemetry-api-*` modules — all `opentelemetry-api-1.*:javaagent`
versions are already bundled in the test agent. The same applies to the other modules listed
under `baseJavaagentLibs` in `javaagent/build.gradle.kts` (e.g. `executors`,
`opentelemetry-instrumentation-annotations-1.16`, and the `internal/*` modules).

Before adding a `testInstrumentation` for a sibling, check
[`javaagent/build.gradle.kts`](../../../javaagent/build.gradle.kts): if the sibling appears
in a `baseJavaagentLibs(...)` line, omit the `testInstrumentation` entry.

### How to check for missing siblings (step by step)

When checking or wiring a `javaagent/` module:

1. Identify the **library grouping directory** — the directory that contains multiple
   versioned subdirectories for the same library. For example, if the module is
   `instrumentation/netty/netty-4.0/javaagent`, the grouping directory is
   `instrumentation/netty`.
2. List the subdirectories of the grouping directory to find candidate modules that
   also have a `javaagent/` subdirectory.
3. **Filter to true version siblings.** A sibling is a module whose directory name shares
   the **same component prefix** and differs **only in the trailing version number**.
   Strip the trailing `-<version>` from each directory name to obtain the component
   prefix. Only modules with the **same** prefix are siblings.

   Examples:
   - `apache-httpclient-2.0`, `apache-httpclient-4.0`, `apache-httpclient-5.0` → prefix
     `apache-httpclient` → **all siblings**.
   - `netty-3.8`, `netty-4.0`, `netty-4.1` → prefix `netty` → **all siblings**.
   - `akka-actor-2.3`, `akka-actor-fork-join-2.5`, `akka-http-10.0` → prefixes
     `akka-actor`, `akka-actor-fork-join`, `akka-http` → **not siblings** of each
     other (different components that happen to share a parent grouping directory).

4. Exclude the current module itself from the filtered list.
5. For each remaining sibling, check whether `build.gradle.kts` contains a
   `testInstrumentation(project(":instrumentation:...:javaagent"))` entry for it.
6. If any sibling is missing, add the `testInstrumentation` dependency and verify by running
   the module's tests.

## Unnecessary Dependencies

When changing `build.gradle.kts` dependencies, remove those confirmed unused
or redundant:

- A `compileOnly` or `implementation` dependency whose classes are not referenced in the module.
- A dependency that duplicates something already provided transitively.
- A `testImplementation` dependency for a library not used in tests.

### Never declare `javaagent-bootstrap` explicitly in javaagent modules

The `otel.javaagent-instrumentation` convention plugin already provides
`javaagent-bootstrap` on the `compileOnly` classpath transitively. Do not add
`compileOnly(project(":javaagent-bootstrap"))` to a javaagent module's
`build.gradle.kts`, and remove it if present.

## Custom Test Tasks

Every custom `Test` task registered with `val foo by registering(Test::class)` **must** include
`testClassesDirs` and `classpath`. Without them the task discovers no test classes and passes
vacuously — a silent false-negative.

```kotlin
val testFoo by registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  // ... other config
}
```

Every such task **must** also be wired into the `check` lifecycle:

```kotlin
check {
  dependsOn(testFoo)
}
```

## Javaagent unit test suites

Tests that exercise javaagent helper or instrumentation classes directly should use a
`JvmTestSuite` whose name ends with `unitTests`. The javaagent testing convention runs these suites
without installing the agent and keeps javaagent classes on the test classpath. Prefer this over a
separate `javaagent-unit-tests` project or reflection used only to bypass the normal agent test
class-loader separation.

```kotlin
testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
```

Put the tests under `src/unitTests`. Declare test-only dependencies inside the suite's
`dependencies` block.

Prefer unconditional `dependsOn(testing.suites)`, including in `testLatestDeps` builds. If existing
wiring is conditional, prefer removing the condition over adding a `unitTests` exception. Repeating
unit or fixed-version tests is acceptable; select suites individually only for a concrete
compatibility or resource constraint.

### Variant tasks in modules with custom `JvmTestSuite`s

`testing.suites` includes the built-in `test` suite alongside any suite the module registers
with `register<JvmTestSuite>(...)`. A variant task bound to `sourceSets.test` covers only the
default source set.

The semconv examples use `<domain>` as a placeholder for a supported selector.
Use `otel.semconv-stability.opt-in=<domain>` for selectable stable conventions or
`otel.semconv-stability.preview=<domain>` for preview conventions. Replace the placeholder
before using the example in a build script; an unrecognized selector does not change the mode.

Derive one variant task per suite only when every suite exercises behavior affected by the
variant and the same task configuration applies to all of them:

```kotlin
val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class)
  .map { suite ->
    register<Test>("${suite.name}StableSemconv") {
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.semconv-stability.opt-in=<domain>")
      systemProperty("metadataConfig", "otel.semconv-stability.opt-in=<domain>")
    }
  }

check {
  dependsOn(testing.suites, stableSemconvSuites)
}
```

The map produces `testStableSemconv` for the built-in suite, so the conventional task name is
preserved. For preview selection, use a `previewSemconvSuites` map that registers
`${suite.name}PreviewSemconv` tasks and sets `otel.semconv-stability.preview=<domain>`.
Declare a separate map per variant when a module has more than one. Use the `StableSemconv`
suffix for stable selection, `PreviewSemconv` for preview selection, and `BothSemconv`
when duplicate-mode coverage is required.
See [testing-semconv-stability.md](testing-semconv-stability.md) for domain-specific modes.

Preserve mixed variants for selectable domains, along with experimental and library-version suites.

#### Preserving source suite JVM settings

When source suite tasks have different JVM arguments or system properties, copy those values into
the variant before adding its own configuration. This avoids rebuilding source settings with
suite-name checks. For a collected telemetry mode, preserve the inherited telemetry conditions and
append the variant's telemetry setting to `metadataConfig`, not every copied JVM setting:

```kotlin
val experimentalSuites = testing.suites.withType(JvmTestSuite::class)
  .map { suite ->
    register<Test>("${suite.name}Experimental") {
      val sourceTask = named<Test>(suite.name).get()
      setJvmArgs(sourceTask.jvmArgs)
      setSystemProperties(sourceTask.systemProperties)

      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      val experimentalConfig = "otel.instrumentation.example.experimental-span-attributes=true"
      jvmArgs("-D$experimentalConfig")
      systemProperty(
        "metadataConfig",
        listOfNotNull(sourceTask.systemProperties["metadataConfig"], experimentalConfig)
          .joinToString(","),
      )
      isEnabled = sourceTask.enabled
    }
  }
```

Use `setJvmArgs` and `setSystemProperties`, not `sourceTask.copyTo(this)`. Gradle's
`JavaForkOptions.copyTo` evaluates the source task's `jvmArgumentProviders` and turns their output
into ordinary JVM arguments. Repository conventions later attach providers to the variant itself.
This can add the javaagent twice and drop the copied providers' input tracking. The variant gets its
own argument providers from the repository conventions.

Points to watch:

- Do not fan a variant out merely because custom suites exist. Keep it bound to
  `sourceSets.test` when the custom suites do not exercise the affected behavior.
- Prefer the uniform map above when every suite is relevant. If only a few custom suites are
  relevant, select those suites explicitly. Per-suite repair blocks are justified only when
  the extra coverage is worth the additional build-script complexity.
- The `testing { suites { … } }` block must appear **before** the `tasks { }` block that maps
  over it. `.map` realizes the container, so suites registered afterwards are silently missed.
- When a source suite task is conditionally disabled, inherit its state in the derived task with
  `isEnabled = sourceTask.enabled` instead of repeating the condition in a separate `named(...)`
  block.
- Keep a variant task bound to a single source set when it is deliberately narrow — one bound
  to a specific suite, or narrowed with `includeTestsMatching(...)`. Fanning such a task across
  every suite fails the build, because Gradle fails a `Test` task whose filter matches nothing.

## `testcontainersBuildService` for Testcontainers Tests

The convention plugin (`otel.java-conventions`) registers a shared build service called
`testcontainersBuildService` with `maxParallelUsages = 2`. This limits how many
Testcontainers-based test tasks run concurrently, preventing Docker resource exhaustion.

Any test task whose tests use Testcontainers **must** declare:

```kotlin
usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
```

Check actual test sources (including shared testing modules) for Testcontainers types such as
`GenericContainer` and `LocalStackContainer`; a dependency declaration alone is not evidence that
the tests use Testcontainers.

Keep shared test configuration in one declaration rather than selecting tasks or variants
individually. In a `JvmTestSuite` execution block, use `executions.configureEach` for settings shared
by the default execution and variants, `executions.configureDefault` for settings limited to the
default execution, and `executions.register` for named variants. A shared declaration can include
container-free variants; do not retain a global `tasks.withType<Test>().configureEach` block merely
to include them. Use `tasks.withType<Test>().configureEach` when the configuration genuinely spans
other suites or independent `Test` tasks outside the selected suite. Keep `tasks.test { ... }`
appropriate for standalone single-test-task projects.

The convention plugin's implicit `latestDepTest` does not by itself require global test
configuration; it inherits the default test task configuration. Keep baseline-only filters and
other task-specific settings in `executions.configureDefault`, and keep variant-only settings on
their registered execution.

```kotlin
tasks {
  withType<Test>().configureEach {
    // ... properties common to all test tasks
  }

  val testPreviewSemconv by registering(Test::class) {
    // only task-specific config here
    jvmArgs("-Dotel.semconv-stability.preview=<domain>")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=<domain>")
  }
}
```

## `collectMetadata` and `metadataConfig`

Follow the [telemetry collection convention](../../../instrumentation-docs/readme.md#telemetry-collection)
for task selection and labels. Shared test conventions forward `-PcollectMetadata=true` to test JVMs.
Keep `metadataConfig` labels local.

Corresponding agent and library variants can use matching task names without sharing configuration
mechanisms or metadata labels. For builder-controlled library options, use test-only fixture flags
and label the actual builder calls, as shown in the
[library variant example](testing-experimental-flags.md#programmatically-configured-library-tests).
For synthetic header setup in agent, plain-library, or library-autoconfigure fixtures, follow the
canonical collection guidance rather than adding capture-only variants or label conditions. Inline
complete labels for fixed profiles; the source-suite variant example above composes labels because it
preserves inherited conditions.
