# [Naming] Module and Package Naming Conventions

Use this article when adding or renaming an instrumentation module or
package, consolidating javaagent modules, or considering enablement-selector
tests. It covers directory and package names, instrumentation selectors, and
the testing boundary for those selectors.

## Top-level instrumentation module directory

- Single-version library: `instrumentation/<library>-<minimum-version>/`
  e.g. `grpc-1.6/`
- No-version module: `instrumentation/<library>/` — **only for JDK / Java standard-library
  instrumentations** where there is no external library version (e.g. `jdbc`, `executors`,
  `http-url-connection`, `java-util-logging`, `rmi`). Do NOT use this pattern for third-party
  libraries.
- Multi-version library: a parent directory `instrumentation/<library>/` containing version
  subdirectories. Each subdir **must be prefixed with the parent dir name**:

  ```
  instrumentation/yarpc/
    yarpc-1.0/
    yarpc-2.0/
  ```

## `settings.gradle.kts` registration

Every subproject must be registered explicitly. Under a parent group:

```kotlin
include(":instrumentation:yarpc:yarpc-1.0:javaagent")
include(":instrumentation:yarpc:yarpc-1.0:library")
include(":instrumentation:yarpc:yarpc-1.0:testing")
include(":instrumentation:yarpc:yarpc-2.0:javaagent")
```

## Submodule leaf names

Standard leaves are `library`, `javaagent`, `testing`.
Special leaves: `bootstrap` (classes needed in the bootstrap class loader).

## Instrumentation enablement selectors

Every name passed to an `InstrumentationModule` constructor is a user-facing
`otel.instrumentation.<name>.enabled` selector. These switches are escape hatches
for disabling buggy instrumentation until a fix is available. Do not recommend
them as a general way to tune telemetry or build selective instrumentation
policies. Keep the naming scheme simple rather than adding convenience groups.

### 3.0 and v3-preview hierarchy

Use the following names, in this order, for new instrumentations and as the
target convention for 3.0/v3-preview:

| Level | Form | Selects |
| ----- | ---- | ------- |
| Family | `<family>` | All modules for the instrumented library across base versions |
| Base version | `<family>-<base-version>` | All modules belonging to that versioned instrumentation |
| Exact module | `<family>-<base-version>-<component>` | One `InstrumentationModule` when the base-version selector covers several |

Names use kebab-case. The family is the instrumented library, not an umbrella
directory or organization. For example, use `akka-actor`, not `akka`. The first
name is the family and normally matches the versioned Gradle module directory
with its version removed:

```java
public MyLibraryInstrumentationModule() {
  super("my-library", "my-library-1.0");
}
```

The base version is the owning versioned instrumentation's baseline. In a
consolidated project, it is not each component's individual minimum supported
library version. Muzzle and runtime matching still enforce those individual
ranges.

If several modules share that base version, each gets an exact selector,
including the core module:

```java
super("my-library", "my-library-1.0", "my-library-1.0-core");
super("my-library", "my-library-1.0", "my-library-1.0-http-client");
```

Omit redundant levels. A base-version selector that already identifies one
module needs no component suffix. JDK instrumentation without a library version
uses `<family>` and, when needed, `<family>-<component>`.

Put component qualifiers after the base version. Prefer a descriptive variant
such as `context-view`. When a component version is needed to distinguish
implementations, put it immediately after that component, for example
`<family>-<base-version>-<component>-<component-version>`. Do not move the base
version after the component, as in `ktor-client-2.0`; the target form is
`ktor-2.0-client`.

Do not add intermediate subgroup selectors, whether they span versions,
Gradle projects, or several modules in one project. For example, the target
Reactor scheme has `reactor`, `reactor-3.1`, and exact selectors such as
`reactor-3.1-core`, `reactor-3.1-context-propagation-operator`, and
`reactor-3.1-context-propagation-operator-context-view`. It does not also have
`reactor-context-propagation-operator` to select both operators. A shared prefix
does not create an implicit selector or wildcard.

### Precedence and declarative configuration

The first explicitly configured name in constructor order wins. With the target
hierarchy, the family overrides the base version, which overrides the exact
module. A narrower selector is effective only when the broader selectors are
unset. If no name is configured, use the module's default enablement.

For example, with the hierarchy above,
`otel.instrumentation.my-library.enabled=true` takes precedence over
`otel.instrumentation.my-library-1.0-http-client.enabled=false`. Do not introduce
most-specific-wins or any-disabled-wins behavior.

Declarative configuration uses the same names with hyphens replaced by
underscores in `distribution.javaagent.instrumentation.enabled` and `disabled`:

```yaml
distribution:
  javaagent:
    instrumentation:
      disabled: [my_library_1.0_http_client]
```

Constructor order determines precedence, not the order of entries in YAML.
If the same name appears in both lists, `disabled` wins for that name. An enabled
broader name still wins over a disabled narrower name.

### Existing names and compatibility

The repository is not yet uniformly aligned with this target. Existing
subgroups include `akka-http-server`, `pekko-http-server-route`, `ktor-client`,
`jaxrs-annotations`, `jersey`, and `resteasy`. Their presence under v3 preview
does not establish an exception to the convention. Alignment belongs in a
deliberate migration, not an incidental rename during unrelated work.

Preserve normal 2.x names, their ordering, which modules they select, and their
warning behavior when introducing a 3.0-only hierarchy. Gate those changes with
v3 preview. A Gradle consolidation must not silently broaden an existing
selector outside preview mode. Legacy aliases retained only outside preview
are compatibility support, not additional levels of the target hierarchy.

For published alias deprecations, follow
[configuration property stability](config-property-stability.md), including
the enablement-specific warning semantics and removal timing.

Keep enablement selectors distinct from Gradle project names and emitted
`otel.scope.name`. Moving a class or changing its selectors does not authorize
renaming its telemetry scope. Preserve the scope and its instrumentation-version
resource lookup unless the change explicitly includes a telemetry migration.
When Muzzle uses an exact selector, ensure it exists in the mode used by Muzzle.

### Testing boundary

Do not add per-instrumentation tests for these escape-hatch selectors. A module
rename, new alias, or Gradle consolidation does not justify:

- Assertions that instantiate modules to check `instrumentationNames()` or its
  ordering.
- Enable/disable matrices for family, version, component, or legacy names,
  including precedence, fallback defaults, and ignored names under v3 preview.
- Per-module repetitions of flat-property and declarative selector resolution
  or deprecation-warning checks.
- Dedicated `javaagent-unit-tests` projects, source sets, JVM variants, YAML
  fixtures, or dependencies solely for those checks.

This applies to installed-agent tests too. Running a library operation in many
JVMs whose only difference is the selector combination still duplicates the
shared enablement mechanism.

Test changes to that shared mechanism centrally, with representative names.
`InstrumentationModuleInstallerTest` already covers ordered flat-property
resolution and fallback defaults; `AgentDistributionConfigTest` covers
declarative resolution and ordering. If shared alias-expansion or warning logic
changes, test the helper rather than repeating its contract for every caller.

Keep tests for actual instrumentation behavior, supported runtime versions,
context propagation, emitted scopes, and Muzzle compatibility. Tests may use
enablement flags to isolate that behavior, for example to disable unrelated
instrumentation, without adding assertions about the flags themselves.

A change to which instrumentation runs by default is a separate behavior
contract. Keep the enabled/disabled operation coverage described in
[default instrumentation enablement](testing-default-enablement.md). Hibernate,
Hystrix, and Twilio have examples of that coverage for v3-preview defaults.
Likewise, feature and experimental-telemetry settings still need their own
behavior coverage; an `.enabled` suffix alone does not make a setting an
instrumentation selector.

## Common modules (shared code across multiple versions)

Three forms exist — pick the right one (standardised in [#16090](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16090)):

| Form                         | When to use                                                            | Example                                                                                |
| ---------------------------- | ---------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `<lib>-common`               | Pure utility / abstraction code with **no** library version dependency | `netty-common`, `ktor-common`, `hibernate-common`, `servlet-common`                    |
| `<lib>-common-<major.minor>` | Shared code that **requires a minimum library version**                | `rxjava-common-3.0`, `netty-common-4.0`, `ktor-common-2.0`, `graphql-java-common-12.0` |
| `<lib>-common-<variant>`     | Shared code tied to an API **variant** (not a version number)          | `servlet-common-javax` (javax vs jakarta split)                                        |

For a module that is part of a sub-group (e.g. Spring, Hibernate), the full group name is the
prefix: `spring-webmvc-common`, `spring-data-common`, `spring-cloud-gateway-common`.

## Package naming

Library instrumentation packages follow the pattern
`io.opentelemetry.instrumentation.<lib>.<subpackage>`:

| Module type                       | Module name            | Java package                                                   |
| --------------------------------- | ---------------------- | -------------------------------------------------------------- |
| version-scoped common (library)   | `rxjava-common-3.0`    | `io.opentelemetry.instrumentation.rxjava.common.v3_0`          |
| version-scoped common (library)   | `ktor-common-2.0`      | `io.opentelemetry.instrumentation.ktor.common.v2_0`            |
| version-scoped common (javaagent) | `netty-common-4.0`     | `io.opentelemetry.javaagent.instrumentation.netty.v4_0.common` |
| non-version common (library)      | `netty-common`         | `io.opentelemetry.instrumentation.netty.common.internal`       |
| non-version common (library)      | `ktor-common`          | `io.opentelemetry.instrumentation.ktor`                        |
| non-version common (javaagent)    | `hibernate-common`     | `io.opentelemetry.javaagent.instrumentation.hibernate`         |
| non-version common (javaagent)    | `spring-webmvc-common` | `io.opentelemetry.javaagent.instrumentation.spring.webmvc`     |

General rules:

- Version in package uses underscores: `v3_0`, `v4_0` — always include the minor version
- `common` appears between the library name and the version segment: `rxjava.common.v3_0`
  NOT `rxjava.v3.common`
- Javaagent packages use `io.opentelemetry.javaagent.instrumentation.<lib>...`
- Library packages use `io.opentelemetry.instrumentation.<lib>...`
- Internal-only classes go in a `.internal` subpackage
