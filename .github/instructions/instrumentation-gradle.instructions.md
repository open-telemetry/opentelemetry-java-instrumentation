---
applyTo: "**/build.gradle.kts,settings.gradle.kts,**/settings.gradle.kts,.github/scripts/instrumentations.sh"
---

# Instrumentation build and test wiring

Apply module-specific checks only to instrumentation projects. Do not comment on a formatting
or test failure that CI will report.

- New module: register each subproject in `settings.gradle.kts`, include supported-library
  documentation and module metadata, choose the correct plugin (`otel.javaagent-instrumentation`,
  `otel.library-instrumentation`, or `otel.java-conventions` for testing), and wire actual test
  variants. A third-party single-version module's directory includes its minimum supported
  version. For multiple versions grouped under a parent directory, prefix each child with the
  parent component name (for example, `yarpc/yarpc-2.0`). Versionless leaves are for JDK
  instrumentation. In shared modules, use a `-common`
  suffix qualified by the minimum version or API variant only when needed. For new javaagent
  modules, check that Muzzle covers their supported ranges and that the main enablement name
  in v3 preview matches the full module directory, including versions, except where a default-off
  feature needs a separate identity within that module. Default-off registrations must not share
  names with default-on instrumentation. Include new telemetry-collection test variants in
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
  the intended classes both outside v3 preview and in preview; otherwise use
  `excludeInstrumentationModule(...)` with fully qualified class names. Public enablement names
  need not distinguish compatibility implementations. Muzzle checks referenced symbols, not
  whether a Byte Buddy method matcher will ever match.
- Versioned javaagent modules for the same component must load their sibling `:javaagent`
  modules via `testInstrumentation` so tests exercise Muzzle selection together. Match the
  component prefix before the trailing version, not just the grouping directory. Omit
  siblings already bundled into `agent-for-testing` through `baseJavaagentLibs` in
  `javaagent/build.gradle.kts`. The convention plugin also supplies `javaagent-bootstrap`;
  an explicit `compileOnly` dependency on it is redundant.
- Every explicitly registered custom `Test` task must set `testClassesDirs` and `classpath`,
  and be included in `check`; without these it can pass without running any tests. For a
  variant of a custom `JvmTestSuite`, preserve source-suite JVM settings and create a variant
  only for suites exercising that behavior. A filtered test task copied to an irrelevant
  suite can select no tests.
- Keep the baseline dependency in `library(...)`. Adding `testLibrary(...)` for a newer version
  of the same coordinate does not test both versions: Gradle resolves one dependency graph,
  normally selecting the higher version. Exercise the newer runtime in a wired version-specific
  test suite.
- Inspect test sources before requiring `testcontainersBuildService`: declare `usesService`
  on tasks that really use containers, not every task with a transitive dependency. Use
  `withType<Test>().configureEach` for configuration shared by multiple explicitly declared
  test tasks; not for a module with only `tasks.test` (implicit `latestDepTest` does not count).
- Metadata collection documents default telemetry and supported telemetry modes, not every test
  configuration. Check it when collection wiring or collected telemetry changes; do not request
  a metadata migration as unrelated cleanup. Collected tasks need
  `systemProperty("collectMetadata", otelProps.collectMetadata)` and registration in
  `.github/scripts/instrumentations.sh` for automated collection. A lone `metadataConfig` label
  does not enable collection.
- For a collected task, set `metadataConfig` only when non-default user-facing settings change the
  documented instrumentation's signals, span kinds, attributes, metrics, or events. Use the actual
  flat `key=value` conditions without `-D`; include all settings needed to describe the mode,
  including inherited settings, separated by commas. Use consistent ordering for the same mode.
  Leave it unset for default telemetry, regardless of the task name; label `test` too if it enables
  non-default telemetry. Do not copy every JVM flag: omit ports, timeouts, JVM access flags, unrelated
  instrumentation settings used to isolate tests, and whole-module enablement used to exercise a
  default-off instrumentation. Include span suppression settings only when they determine the
  documented instrumentation's telemetry. `metadataConfig` is a label, not runtime configuration;
  verify the task separately applies its settings. Missing labels merge optional telemetry into
  `when: default`, while setup-only labels create misleading configuration requirements.
- Do not collect unit-test suites or regression-only disablement, adapter-fallback, or classpath
  compatibility variants. Keep regression coverage in `check`, but omit these tasks from
  `.github/scripts/instrumentations.sh` and do not give them `metadataConfig`. If a shared block
  passes through `collectMetadata`, override it with `systemProperty("collectMetadata", false)` on
  excluded tasks. Omitting only `metadataConfig` records their telemetry under `default`; it does
  not exclude collection. A Camel adapters-disabled regression task, for example, does not need a
  profile listing the disabled adapters. Do not infer regression-only status from a task name when
  its tests actually document a supported telemetry mode.
- When tests exercise behavior behind an experimental feature or telemetry flag, including
  experimental metrics, cover default-off and flag-on modes in separate JVMs. Keep the flag off
  the default test task and run the flag-on assertions through a wired `testExperimental` task
  or an existing equivalent variant. Do not request a task for flags unrelated to the tests or
  another task when the default test and an existing wired variant already cover both modes.
  Semconv selection assertions need a task for the relevant stable or preview mode. Use
  `otel.semconv-stability.opt-in=<domain>` for selectable stable conventions and
  `otel.semconv-stability.preview=<domain>` for preview conventions, replacing `<domain>` with a
  supported selector. Name the tasks `testStableSemconv` for stable selection and
  `testPreviewSemconv` for preview selection; define both when both are exercised. Preserve explicit
  legacy opt-in compatibility tests. `/dup` coverage is required for RPC, not service-peer.
  Code and database conventions are stable-only and need no selection task. Keep mixed variants
  that exercise selectable domains. For default enablement under
  v3-preview, use a separate `testDisabled` JVM rather than setting a property after agent
  startup. Because `testDisabled` intentionally emits no target instrumentation telemetry, do
  not add it to `.github/scripts/instrumentations.sh` or give it `collectMetadata` /
  `metadataConfig`.
