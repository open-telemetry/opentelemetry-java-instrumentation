# [Naming] Module and Package Naming Conventions

Use this article when adding or renaming an instrumentation module or
package. It shows the directory, Gradle include, and Java package naming
patterns for versioned and shared modules.

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

## `InstrumentationModule` name

In v3 preview, the first (main) name passed to `super()` normally equals the full Gradle module
directory name, including versions. The version-stripped component name is a secondary:

```java
public MyLibraryInstrumentationModule() {
  super(
      AgentCommonConfig.get().isV3Preview() ? "my-library-1.0" : "my-library",
      AgentCommonConfig.get().isV3Preview()
          ? new String[] {"my-library"}
          : new String[] {"my-library-1.0"});
}
```

Module names use `kebab-case`.

Optional role and feature selectors are versionless, such as `ratpack-client` or `aws-sdk-sqs`,
and precede component secondaries. Compatibility helpers share their parent's names.
Implementation splits such as `mongo-async-3.3` retain their directory primary but use only
`mongo` as a secondary, without `mongo-async`.

Compound API/provider modules keep the full compound primary, its version-stripped secondary,
and the API umbrella. For example, `jaxrs-2.0-cxf-3.2`, `jaxrs-cxf`, `jaxrs`, without bare provider
or API-generation aliases.

Default-off features have independent names when the component family also contains default-on
instrumentation. For example, JDBC's default-off DataSource instrumentation uses only
`jdbc-datasource`, not the shared `jdbc` selector. Product umbrellas such as `vertx` come after
the component's own selectors and must not mix default-on and default-off instrumentation.
Standalone default-off feature modules use their full directory name as the primary,
followed by the versionless feature name. For example, `jaxrs-1.0-annotations` precedes
`jaxrs-annotations`; neither shares `jaxrs` with default-on provider instrumentation.

The generic Reactor Netty server registration housed in `spring-webflux-5.0` uses primary
`spring-webflux-5.0` and secondaries `reactor-netty-server`, `spring-webflux-server`, and
`spring-webflux`, without `reactor-netty` or `reactor`. This cross-family selector exception applies
only to that registration.
Outside v3 preview, preserve existing names and their order.

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
