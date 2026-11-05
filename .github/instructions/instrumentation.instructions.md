---
applyTo: "instrumentation/**/*,instrumentation-api/**/*,instrumentation-api-incubator/**/*,settings.gradle.kts"
---

# Instrumentation contracts

Apply these rules when adding or changing instrumentation modules and packages.

## Module boundaries

- Instrumentation Gradle modules can support multiple library versions, including major versions.
- Separate modules are needed when compatibility or build constraints prevent sharing.

## Directories and subprojects

- A third-party single-version module includes its minimum supported version, such as `grpc-1.6`.
  Versionless modules are for JDK/standard-library instrumentation, such as `jdbc` or `executors`.
- Multiple versions grouped under a parent repeat the component prefix in each child, such as
  `yarpc/yarpc-1.0` and `yarpc/yarpc-2.0`.
- Standard subproject leaves are `javaagent`, `library`, and `testing`; `bootstrap` contains
  helpers that must be visible from the bootstrap loader. Register subprojects explicitly in
  `settings.gradle.kts`.
- Shared modules use `<library>-common` for version-independent utilities,
  `<library>-common-<major.minor>` when they require that minimum library version, and
  `<library>-common-<variant>` for an API variant such as `servlet-common-javax`.
  Preserve the full component/group prefix, such as `spring-cloud-gateway-common`.

## Package naming

- Library packages use `io.opentelemetry.instrumentation.<library>...`; javaagent packages use
  `io.opentelemetry.javaagent.instrumentation.<library>...`.
- Encode version segments as one identifier such as `v4_0`, not dotted package segments such as
  `v4.0`.
- In versioned common modules, put `common` before the version in both javaagent and library
  packages (`io.opentelemetry.javaagent.instrumentation.<library>.common.v4_0` and
  `io.opentelemetry.instrumentation.<library>.common.v4_0`). A non-versioned javaagent common
  module uses the ordinary javaagent package without a trailing `common` segment; a non-versioned
  library common package may retain one.
