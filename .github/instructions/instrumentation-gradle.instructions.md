---
applyTo: "**/build.gradle.kts,settings.gradle.kts,**/settings.gradle.kts"
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
  matches the module directory without its version suffix. Include new test variants in
  `.github/scripts/instrumentations.sh`, keep `settings.gradle.kts` entries alphabetical,
  add the supported-library entry and module READMEs where applicable, and regenerate
  `.fossa.yml` with `generateFossaConfiguration` when adding a module.
- Muzzle `pass` blocks need the target group, artifact, version range and inverse assertion
  where an inverse exists. A pass covering all versions has no meaningful inverse. If
  multiple `InstrumentationModule`s share a project, separate their ranges and exclude
  unrelated instrumentation names in each pass. Muzzle checks referenced symbols, not whether
  a Byte Buddy method matcher will ever match.
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
- If metadata-collection properties are already present, ensure non-default tasks describe
  the actual JVM setting in `metadataConfig` and also enable `collectMetadata`. Do not ask
  for these properties merely as cleanup or on unit-test suites.
- For experimental-attribute coverage, do not enable the experimental flag for the default
  test task and call that both modes; use a wired `testExperimental` task. Semconv opt-in
  assertions need a stable-mode task for the relevant domain; `/dup` coverage is required
  for RPC, not database, code, or service-peer. For default enablement under v3-preview,
  use a separate `testDisabled` JVM rather than setting a property after agent startup.
  Because `testDisabled` intentionally emits no target instrumentation telemetry, do not add it
  to `.github/scripts/instrumentations.sh` or give it `collectMetadata` / `metadataConfig`.
