# [Build] Gradle Conventions

## Quick Reference

- Use when: reviewing `build.gradle.kts`, `settings.gradle.kts`, or Gradle test tasks
- Review focus: muzzle config, plugin type, include ordering, test task wiring, `withType` usage

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

### Important: do not remove existing `assertInverse` or `skip`

- Never remove an existing `assertInverse.set(true)` from a `pass` block.
- Never remove existing `skip()` entries — they apply to both the `pass` range and the
  auto-generated `assertInverse` check. Removing them causes the inverse to test broken
  versions that will fail.

## Gradle Plugin Verification

Verify `build.gradle.kts` applies the correct plugin for the module type:

| Module type | Required plugin |
| --- | --- |
| `javaagent/` | `otel.javaagent-instrumentation` |
| `library/` | `otel.library-instrumentation` |
| `testing/` | `otel.java-conventions` |

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

Examples:

```kotlin
if (otelProps.testLatestDeps) {
  // ...
}

tasks.withType<Test>().configureEach {
  systemProperty("collectMetadata", otelProps.collectMetadata)
}
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
from a sibling's `build.gradle.kts` is redundant and should be rejected in review.

In particular, do not add `testInstrumentation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.N:javaagent"))`
entries to sibling `opentelemetry-api-*` modules — all `opentelemetry-api-1.*:javaagent`
versions are already bundled in the test agent. The same applies to the other modules listed
under `baseJavaagentLibs` in `javaagent/build.gradle.kts` (e.g. `executors`,
`opentelemetry-instrumentation-annotations-1.16`, and the `internal/*` modules).

Before adding a `testInstrumentation` for a sibling, check
[`javaagent/build.gradle.kts`](../../../javaagent/build.gradle.kts): if the sibling appears
in a `baseJavaagentLibs(...)` line, omit the `testInstrumentation` entry.

### How to check for missing siblings (step by step)

When reviewing a `javaagent/` module:

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

Flag `build.gradle.kts` dependencies that appear unused or redundant:

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

## `testcontainersBuildService` for Testcontainers Tests

The convention plugin (`otel.java-conventions`) registers a shared build service called
`testcontainersBuildService` with `maxParallelUsages = 2`. This limits how many
Testcontainers-based test tasks run concurrently, preventing Docker resource exhaustion.

Any test task whose tests use Testcontainers **must** declare:

```kotlin
usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
```

Place the declaration in `withType<Test>().configureEach` when **all** test tasks in the
module use Testcontainers **and** the module has multiple test tasks. When the module has
only a single test task, put the declaration directly inside `tasks.test { ... }` — do not
introduce a `withType<Test>().configureEach` block just for `usesService`. Otherwise place
it on **only** the specific task(s) that use Testcontainers.

**Do not over-apply.** Adding `usesService` to a task that does not use Testcontainers
unnecessarily throttles it against the 2-slot concurrency limit. A module may have some
suites that use Testcontainers (e.g., `LocalStackContainer`) and others that use in-process
test servers (e.g., ElasticMQ `SQSRestServerBuilder`, MockWebServer, WireMock). Only the
Testcontainers-using suites need the declaration.

**How to detect a missing declaration:** if a test task's source set (or the shared testing
module it extends) imports or instantiates Testcontainers types (`GenericContainer`,
`LocalStackContainer`, etc.), it needs `usesService`. Check the actual test sources — do not
rely solely on the presence of an `org.testcontainers:*` dependency in `build.gradle.kts`,
because the dependency may only be used by some suites in the module.

## Prefer `withType<Test>().configureEach` (ONLY when multiple test tasks exist)

When a module has custom test tasks (e.g., `testStableSemconv`), shared configuration
(system properties, JVM args, `usesService` declarations, etc.) that applies to **all**
test tasks should be set once in a `withType<Test>().configureEach` block, not repeated
on each individual task.

If a property or JVM arg is moved into `withType<Test>().configureEach`, remove any now-redundant
copies from individual tasks unless a task intentionally overrides the shared value.

**When the module has only a single test task, prefer the simple `tasks.test { ... }` form.**
Do **not** convert `tasks.test { ... }` to `withType<Test>().configureEach` in single-test-task
modules, and do **not** flag the simple form as a problem. The `withType<Test>().configureEach`
form is only justified when the same `build.gradle.kts` actually registers additional `Test` tasks.

**`latestDepTest` does not count as a second test task for this rule.** It is registered
implicitly by the convention plugin when `testLatestDeps` is set, and it inherits the
configuration of `tasks.test`. A module with only a `tasks.test { ... }` block and no
`by registering(Test::class)` declarations is a single-test-task module — leave it alone
(use the simple form) even if `testLatestDeps = true`.

Only consider converting to `withType<Test>().configureEach` when the **same
`build.gradle.kts`** explicitly registers one or more additional `Test` tasks via
`val foo by registering(Test::class)`.

**How to spot violations:** If `build.gradle.kts` has both a `test { ... }` block and an
explicit `by registering(Test::class)` custom test task (e.g., `testStableSemconv`), check
whether any `systemProperty(...)` or `jvmArgs(...)` calls appear inside the `test { }` block
that should apply to all tasks. If so, move them to `withType<Test>().configureEach`.

```kotlin
tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    // ... other properties common to all test tasks
  }

  val testStableSemconv by registering(Test::class) {
    // only task-specific config here
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }
}
```

## `collectMetadata` and `metadataConfig`

These system properties support the metadata collection pipeline. They are not required for
test correctness and are being added as a separate migration — **do not add them during
review**. Only verify correctness when they are already present.

Do not add `collectMetadata` or `metadataConfig` to `javaagent-unit-tests` projects. These are
unit tests, and metadata collection should not run there.

| Property | Type | Value |
| --- | --- | --- |
| `collectMetadata` | System property | Pass-through of `otelProps.collectMetadata`; defaults to `false` |
| `metadataConfig` | System property | A single `key=value` string describing the non-default configuration active during this test run |

When already present, verify:

- `metadataConfig` is only used in files that also configure `collectMetadata`. A lone
  `metadataConfig` does not enable collection and should be removed, not added as a partial
  metadata migration.
- `collectMetadata` is in `tasks.test` for single-test-task modules, or in
  `withType<Test>().configureEach` for modules that explicitly register additional `Test`
  tasks via `by registering(Test::class)` (`latestDepTest` does not count) — never on
  individual tasks. Do not use
  `withType<Test>().configureEach { ... }` in single-test-task modules.
- `metadataConfig` is on each non-default task that participates in metadata collection. It may
  also appear on the default `test` task when that task participates in metadata collection and
  itself runs with non-default `jvmArgs` (e.g., an experimental flag enabled module-wide via
  `withType<Test>().configureEach { jvmArgs(...) }`); in that case the `metadataConfig` value
  should describe those non-default jvmArgs.
- The `metadataConfig` value matches at least one of the jvmArgs configured in the task
