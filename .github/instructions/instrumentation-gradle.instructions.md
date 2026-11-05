---
applyTo: "**/build.gradle.kts,settings.gradle.kts,**/settings.gradle.kts,.github/scripts/instrumentations.sh"
---

# Instrumentation build and test wiring

Apply module-specific rules only to instrumentation projects. Test-task wiring rules also apply
to shared infrastructure and other projects using the same conventions.

- New module: register each subproject in `settings.gradle.kts`, include supported-library
  documentation and module metadata, choose the correct plugin (`otel.javaagent-instrumentation`,
  `otel.library-instrumentation`, or `otel.java-conventions` for testing), and wire actual test
  variants. New javaagent modules need Muzzle coverage for their supported ranges.
  Include new telemetry-collection test variants in
  `.github/scripts/instrumentations.sh`, keep `settings.gradle.kts` entries alphabetical,
  add the supported-library entry, and regenerate `.fossa.yml` with
  `generateFossaConfiguration` when adding a module. For a new javaagent module with user-facing
  settings, document them in a settings table in its `javaagent/README.md` or a shared parent
  README. A standalone library instrumentation needs a library README with dependency and
  usage details.
- Muzzle `pass` blocks need the target group, artifact, version range and inverse assertion
  where an inverse exists. A pass covering all versions has no meaningful inverse. If
  multiple `InstrumentationModule`s share a project, separate their ranges and exclude
  unrelated modules in each pass. Prefer `excludeInstrumentationName(...)` when the name selects
  the intended classes; otherwise use
  `excludeInstrumentationModule(...)` with fully qualified class names. Public enablement names
  need not distinguish compatibility implementations. Muzzle checks referenced symbols, not
  whether a Byte Buddy method matcher will ever match.
  Keep existing inverse checks and skips unless the compatibility contract itself changes;
  skips apply to both the pass range and its inverse.
- Versioned javaagent modules for the same component must load their sibling `:javaagent`
  modules via `testInstrumentation` so tests exercise Muzzle selection together. Match the
  component prefix before the trailing version, not just the grouping directory. Omit
  siblings already bundled into `agent-for-testing` through `baseJavaagentLibs` in
  `javaagent/build.gradle.kts`. The convention plugin also supplies `javaagent-bootstrap`;
  an explicit `compileOnly` dependency on it is redundant.
- Every explicitly registered custom `Test` task must set `testClassesDirs` and `classpath`;
  otherwise it can pass without running any tests. Include normal verification tasks in `check`,
  but preserve intentionally opt-in tasks. Prefer suite execution variants over manually copying
  a suite into another `Test` task. Keep `tasks.test` appropriate for standalone single-test-task
  projects.
- Keep the baseline dependency in `library(...)`. Adding `testLibrary(...)` for a newer version
  of the same coordinate does not test both versions: Gradle resolves one dependency graph,
  normally selecting the higher version. Exercise the newer runtime in a wired version-specific
  test suite.
- Apply `otel.testcontainers-testing` in projects whose tests use Testcontainers, including through
  shared fixtures; a transitive dependency alone is not enough. The plugin limits all `Test` tasks
  in the project, including container-free suites and variants. Use narrower scheduling only for a
  demonstrated problem, and keep unrelated test settings scoped to the tasks that need them.
- Check metadata collection when its wiring or collected telemetry changes, not as unrelated
  cleanup. Collect default telemetry and supported non-default telemetry modes. Register
  representative tasks in `.github/scripts/instrumentations.sh` covering each profile's
  instrumentation scope and telemetry shape; equivalent suites need not all collect. Shared test
  conventions forward `-PcollectMetadata=true` to test JVMs.
- `metadataConfig` labels output; verify settings are applied separately. Leave it unset for
  instrumentation defaults, regardless of task name or settings on `test`.
  Otherwise include only non-default user-facing settings
  needed to describe this instrumentation's signals, span kinds, attributes, metrics, or events,
  including inherited settings, except for the shared synthetic header capture described below.
  Inspect JVM properties, fixtures, and library builders; split or
  filter collected tasks that mix profiles under one label. Label non-default metric selectors,
  not Java source-set names; document runtime prerequisites separately. For actual JVM-property
  configuration, use flat `key=value` conditions without `-D`. For programmatically configured
  library options, label the actual builder calls, such as
  `AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)`, not test-only flags or
  equivalent javaagent properties that do not configure the library. Separate conditions and
  builder calls with commas, using consistent spelling and ordering for the same mode.
  Omit test setup, unrelated instrumentation settings, and whole-module enablement for default-off
  instrumentation. Include span suppression settings only when they determine this instrumentation's
  telemetry. Missing labels merge optional telemetry into `when: default`.
- For javaagent, plain-library, and library-autoconfigure tests, baseline fixtures may capture
  synthetic HTTP and messaging headers through test-exporter configuration, a library builder, or
  production properties the integration actually reads. This fixture setup does not require empty
  overrides, capture-only flags or variants, or `metadataConfig` conditions.
  Follow the [canonical collection guidance](../../instrumentation-docs/readme.md#telemetry-collection)
  for synthetic capture and profile labels. Test meaningful opt-in telemetry in named variants wired
  into `check`; preserve normal suite coverage.
- Exclude unit tests and regression-only disablement, adapter-fallback, or classpath compatibility
  variants based on their purpose, not their names. A full-suite `testReceiveSpansDisabled` may
  document default telemetry; a suppression-only regression task does not. Keep regression tasks in
  `check`, but omit them from `.github/scripts/instrumentations.sh` and do not give them `metadataConfig`.

## Compatibility ranges and dependency graphs

Prefer disjoint ranges for mutually exclusive compatibility implementations. Additive or
higher-subrange behavior whose baseline remains compatible can stay in the same javaagent project,
with separate `InstrumentationModule` classes and Muzzle passes. A different test matrix, helper
project, Scala variant, or optional feature alone does not require another javaagent project.
Incompatible toolchains/plugins and version-specific generated or shaded compile classpaths can
require a separate javaagent project. Keep newer optional compile-time types in `compileOnly` when
appropriate.

For version siblings, compare the component prefix before the trailing version, not just the parent
directory. `netty-3.8` and `netty-4.1` are siblings; `akka-actor-2.3` and `akka-http-10.0` are not.
Check `baseJavaagentLibs` before adding sibling `testInstrumentation` dependencies.

`library(...)` supplies the baseline compile-only dependency and default test runtime.
`testLibrary(...)` is valid for a deliberately different single runtime or test-only artifact.
To test two runtimes, leave baseline-compatible tests in `src/test`, put only newer-runtime tests
in a dedicated `JvmTestSuite`, declare its runtime dependency in that suite, and wire the suites
into `check`. Do not count direct helper unit tests as real installed-agent coverage.

## Shared project properties

Use `otelProps` for repository properties it already models, including `testLatestDeps`,
`denyUnsafe`, `collectMetadata`, `testJavaVersion`, `testJavaVM`, `maxTestRetries`, and
`enableStrictContext`. Module-local properties not modeled there can use `findProperty(...)`.
`settings.gradle.kts` cannot use project extensions and reads
`gradle.startParameter.projectProperties[...]` directly.

Forward `systemProperty("testLatestDeps", otelProps.testLatestDeps)` only to suites whose tests or
used shared fixtures read it. `latestDepTestLibrary(...)` controls Gradle dependency resolution
and does not itself require runtime forwarding.

## Test suites and executions

### Layout and configuration

Use `JvmTestSuite` for distinct sources or dependency graphs, and execution variants for different
flags or filters over a suite's existing compiled tests and runtime dependencies. Variants add no
default task, compilation, or source set.

Use one `testing.suites { ... }` container per build: all registrations first, then collection-level
`executions(...)` declarations. Keep dependencies, sources, and compilation setup in registrations;
JVM settings, filters, and variants belong in execution blocks, not nested in `register<JvmTestSuite>`
or `named<JvmTestSuite>`. Select the built-in suite with `executions("test")`.

Declare one executions block per suite, counting shared selectors. Use `executions("suiteA", "suiteB")`
to share identical configuration; unqualified `executions { ... }` applies to every JVM suite,
including later registrations. Use that scope only intentionally. Do not duplicate shared blocks,
apply filtered or behavior-specific variants to unrelated suites, or rebuild common settings with
suite-name checks.

`configureEach` applies suite-wide settings, even without variants; `configureDefault` applies
baseline-only settings that variants must not inherit. `register("Suffix")` configures a variant.
Common configuration precedes default-only or variant-specific configuration.

```kotlin
import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

testing.suites {
  register<JvmTestSuite>("integrationTest") {
    dependencies {
      implementation(project())
    }
  }

  executions("test", "integrationTest") {
    configureEach {
      jvmArgs("-Xss2m")
    }
    register("Experimental") {
      telemetryConfig("otel.instrumentation.example.experimental-span-attributes", "true")
    }
  }
}

tasks.check {
  dependsOn(testing.suites)
}
```

Scope JVM opens, JDK/`denyUnsafe` gates, agent setup, inputs, and argument providers to their consumers.
Keep genuinely cross-suite `tasks.withType<Test>().configureEach` settings shared.

`telemetryConfig` sets a production JVM property and composes its `metadataConfig` profile.
Do not use it for test-only switches selecting library builder options.

### Lifecycle and coverage

Gradle wires built-in `test` into `check`, but not custom suite registrations. Execution configuration
adds its suite's default and variants when that suite is realized; applying the plugin or declaring a
lazy selector is not sufficient. Prefer unconditional `check { dependsOn(testing.suites) }` when all
suites belong in `check`, including latest-dependency builds. Retain dependencies needed to realize
and include suites; extra wiring is unnecessary when they are already reliably included.
Wire standalone `Test` tasks separately.

Preserve conditional execution declarations and compatibility/JDK/resource selection when moving
blocks: an unconditional executions call can add otherwise unselected suites to `check`. Apply JDK
gates to every execution requiring them, using the configured test JVM rather than the build JVM.
Do not migrate opt-in smoke-test matrices or benchmarks into `check`.

Combine mutually selected baseline/latest suites only with equivalent sources/behavior and unchanged
dependency coverage, using existing version-selection helpers. Keep simultaneously tested runtimes
and distinct compatibility paths separate.

### Dependencies, classpaths, and naming

For direct javaagent helper tests, use a `unitTests` suite under `src/unitTests/java` with
`implementation(project())`, without installing the agent. Keep test-only dependencies local and reuse
convention-provided dependencies and compiler options, such as testing-common and `-parameters`.

Prefer suite-local dependencies and compilation setup. When configuration/task names are needed,
use `sources.implementationConfigurationName`, `sources.runtimeOnlyConfigurationName`, and
`sources.compileJavaTaskName`.

- Preserve fixture outputs, dependency inheritance, compiler releases, and packaging. Remove explicit
  compile/task dependencies only after verifying that source-set outputs retain the ordering;
  disabling newer-JVM tests does not remove compilation requirements.
- Preserve multi-release sources, sources JAR entries, and manifests.
- Use full suite output, including resources, in custom runtime classpaths. Preserve pinned/shaded
  dependencies and lazy resolution; create dependency configurations outside lazy task actions.
- Preserve artifact inputs/normalizers and JVM argument providers. For necessary legacy JVM-setting
  copies, use `setJvmArgs` and `setSystemProperties`, not `JavaForkOptions.copyTo`, which evaluates
  providers and can duplicate the javaagent or lose input tracking.

`register("Suffix")` names a variant after its owning suite plus the suffix; it shares the suite's
source directory. Version-suite names should describe the runtime tested. Do not normalize existing
version separators or add APIs solely to preserve inconsistent internal task names. Update all
task/source callers and metadata-collection references together when renaming.

Prefer the DSL to manual suite-to-task maps and unnecessary helpers; retain useful suffix/version
maps and deterministic discovered-class loops.

### Verification and scheduling

Check default/variant configuration and `check` graphs in relevant baseline/latest/JDK modes,
including explicit suite realization. Verify real discovery and execution after filter changes:
default-only exclusions must stay off variants, and no-test failure behavior must be preserved.

Remove locks, build services, or environment setup only after verifying current framework
initialization and concurrent-test behavior. Preserve real shared-workspace/Testcontainers
constraints; old comments alone do not establish their necessity.
