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

### How to check for missing siblings (step by step)

When reviewing a `javaagent/` module:

1. Identify the **library grouping directory** — the directory that contains multiple
   versioned subdirectories for the same library. For example, if the module is
   `instrumentation/netty/netty-4.0/javaagent`, the grouping directory is
   `instrumentation/netty`.
2. List the subdirectories of the grouping directory to find sibling version modules that
   also have a `javaagent/` subdirectory. For example, `instrumentation/netty/` contains
   `netty-3.8/javaagent`, `netty-4.0/javaagent`, and `netty-4.1/javaagent`.
3. Exclude the current module itself from the list.
4. For each remaining sibling, check whether `build.gradle.kts` contains a
   `testInstrumentation(project(":instrumentation:...:javaagent"))` entry for it.
5. If any sibling is missing, add the `testInstrumentation` dependency and verify by running
   the module's tests.

## Unnecessary Dependencies

Flag `build.gradle.kts` dependencies that appear unused or redundant:

- A `compileOnly` or `implementation` dependency whose classes are not referenced in the module.
- A dependency that duplicates something already provided transitively.
- A `testImplementation` dependency for a library not used in tests.

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

Place the declaration in `withType<Test>().configureEach` when all test tasks in the module
use Testcontainers. Otherwise place it on the specific task(s) that do.

**How to detect a missing declaration:** if `build.gradle.kts` has a dependency on any
`org.testcontainers:*` artifact (directly or via a `testing` module that pulls one in) but
no test task calls `usesService(...)` for `testcontainersBuildService`, flag it.

## Prefer `withType<Test>().configureEach` (when multiple test tasks exist)

When a module has custom test tasks (e.g., `testStableSemconv`), system properties and JVM
args that apply to **all** test tasks should be set once in a `withType<Test>().configureEach`
block, not repeated on each individual task.

When there is only one test task, `tasks.test { ... }` is fine.

**How to spot violations:** If `build.gradle.kts` has both a `test { ... }` block and a
custom test task (e.g., `val testStableSemconv by registering(Test::class)`), check whether
any `systemProperty(...)` or `jvmArgs(...)` calls appear inside the `test { }` block that
should apply to all tasks. If so, move them to `withType<Test>().configureEach`.

```kotlin
tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
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

| Property | Type | Value |
| --- | --- | --- |
| `collectMetadata` | System property | Pass-through of the `collectMetadata` Gradle project property; defaults to `"false"` |
| `metadataConfig` | System property | A single `key=value` string describing the non-default configuration active during this test run |

When already present, verify:

- `collectMetadata` is in `withType<Test>().configureEach` (or `tasks.test` if only one test
  task) — never on individual tasks.
- `metadataConfig` is on each non-default task, not on the default `test` task.
- The `metadataConfig` value matches at least one of the jvmArgs configured in the task
