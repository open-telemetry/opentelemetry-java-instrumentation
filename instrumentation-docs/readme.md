# Doc Generator

Runs analysis on instrumentation modules in order to generate documentation.

## How to use

Run the doc generator:

`./gradlew :instrumentation-docs:generateDocs`

## Instrumentation Hierarchy

An "InstrumentationEntity" represents a module that that targets specific code in a framework/library/technology.
Each instrumentation uses muzzle to determine which versions of the target code it supports.

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

* Name
  * Ex: `clickhouse-client-05`, `jaxrs-1.0`, `spring-cloud-gateway-2.0`
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

* name
  * Identifier for instrumentation module, used to enable/disable
  * Configured in `InstrumentationModule` code for each module
* srcPath
  * Path to the source code of the instrumentation module
* description
  * Short description of what the instrumentation does
* target_versions
  * List of supported versions by the module, broken down by `library` or `javaagent` support
* scope
  * Name: The scope name of the instrumentation, `io.opentelemetry.{instrumentation name}`

## Methodology

### metadata.yaml file

Within each instrumentation source directory, a `metadata.yaml` file can be created to provide
additional information about the instrumentation module.

As of now, the following fields are supported:

```yaml
description: "Description of what the instrumentation does."
```

### Versions targeted

We parse gradle files in order to determine the target versions.

- Javaagent versions are determined by the `muzzle` plugin configurations
- Library versions are determined by the library dependency versions
  - when available, latestDepTestLibrary is used to determine the latest supported version

### Scope

For now, the scope name is the only value that is implemented in our instrumentations. The scope
name is determined by the instrumentation module name:  `io.opentelemetry.{instrumentation name}`

We will implement gatherers for the schemaUrl and attributes when instrumentations start
implementing them.
