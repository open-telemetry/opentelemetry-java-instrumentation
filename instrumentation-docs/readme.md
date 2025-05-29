# Doc Generator

Runs analysis on instrumentation modules in order to generate documentation.

## How to use

Run the analysis to update the instrumentation-list.yaml:

`./gradlew :instrumentation-docs:runAnalysis`

## Instrumentation Hierarchy

An "InstrumentationModule" represents a module that that targets specific code in a
framework/library/technology. Each module will have a name, a namespace, and a group.

Using these structures as examples:

```
├── instrumentation
│   ├── clickhouse-client-05
│   ├── jaxrs
│   │   ├── jaxrs-1.0
│   │   ├── jaxrs-2.0
│   ├── spring
│   │   ├── spring-cloud-gateway
│   │   │   ├── spring-cloud-gateway-2.0
│   │   │   ├── spring-cloud-gateway-2.2
│   │   │   └── spring-cloud-gateway-common
```

Results in the following:

* Name - the full name of the instrumentation module
  * `clickhouse-client-05`, `jaxrs-1.0`, `spring-cloud-gateway-2.0`
* Namespace - direct parent. if none, use name and strip version
  * `clickhouse-client`, `jaxrs`, `spring-cloud-gateway`
* Group - top most parent
  * `clickhouse-client`, `jaxrs`, `spring`

This information is also referenced in `InstrumentationModule` code for each module:

```java
public class SpringWebInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringWebInstrumentationModule() {
    super("spring-web", "spring-web-3.1");
  }
```

## Instrumentation metadata

* classification
  * `library` - Instrumentation that targets a library
  * `internal` - Instrumentation that is used internally by the OpenTelemetry Java Agent
  * `custom` - Utilities that are used to create custom instrumentation
* name
  * Identifier for instrumentation module, used to enable/disable
  * Configured in `InstrumentationModule` code for each module
* source_path
  * Path to the source code of the instrumentation module
* minimum_java_version
  * Minimum Java version required by the instrumentation module. If not specified, it is assumed to
    be Java 8
* description
  * Short description of what the instrumentation does
* target_versions
  * List of supported versions by the module, broken down by `library` or `javaagent` support
* scope
  * Name: The scope name of the instrumentation, `io.opentelemetry.{instrumentation name}`
* configurations settings
  * List of settings that are available for the instrumentation module
  * Each setting has a name, description, type, and default value

## Methodology

### metadata.yaml file

Within each instrumentation source directory, a `metadata.yaml` file can be created to provide
additional information about the instrumentation module.

As of now, the following fields are supported, all of which are optional:

```yaml
description: "Instruments..."   # Description of the instrumentation module
disabled_by_default: true       # Defaults to `false`
classification: internal        # instrumentation classification: library | internal | custom
configurations:
  - name: otel.instrumentation.common.db-statement-sanitizer.enabled
    description: Enables statement sanitization for database queries.
    type: boolean               # boolean | string | list | map
    default: true
```

### Gradle File Derived Information

We parse gradle files in order to determine several pieces of metadata:

- Javaagent versions are determined by the `muzzle` plugin configurations
- Library versions are determined by the library dependency versions
  - when available, latestDepTestLibrary is used to determine the latest supported version
- Minimum Java version is determined by the `otelJava` configurations

### Scope

For now, the scope name is the only value that is implemented in our instrumentations. The scope
name is determined by the instrumentation module name:  `io.opentelemetry.{instrumentation name}`

We will implement gatherers for the schemaUrl and scope attributes when instrumentations start
implementing them.
