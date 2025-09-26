# Doc Generator

Runs analysis on instrumentation modules in order to generate documentation.

## How to use

Run the analysis to update the instrumentation-list.yaml:

`./gradlew :instrumentation-docs:runAnalysis`

### Telemetry collection

Until this process is ready for all instrumentations, each module will be modified to include a
system property feature flag configured for when the tests run. By enabling the following flag you
will enable metric and span collection:

```kotlin
tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    ...
  }
}
```

Sometimes instrumentation will behave differently based on configuration options, and we can
differentiate between these configurations by using the `metadataConfig` system property. When the
telemetry is written to a file, the value of this property will be included, or it will default to
a `default` attribution.

For example, to collect and write metadata for the `otel.semconv-stability.opt-in=database` option
set for an instrumentation:

```kotlin
val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  test {
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
```

Then, prior to running the analyzer, run the following command to generate `.telemetry` files:

`./gradlew test -PcollectMetadata=true`

Then run the doc generator

`./gradlew :instrumentation-docs:runAnalysis`

or use the helper script that will run only the currently supported tests (recommended):

```bash
./instrumentation-docs/collect.sh
```

## Instrumentation Hierarchy

An "InstrumentationModule" represents a module that targets specific code in a
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
* semantic_conventions
  * The semantic conventions that the instrumentation module adheres to
  * Options are:
    * HTTP_CLIENT_SPANS
    * HTTP_CLIENT_METRICS
    * HTTP_SERVER_SPANS
    * HTTP_SERVER_METRICS
    * RPC_CLIENT_SPANS
    * RPC_CLIENT_METRICS
    * RPC_SERVER_SPANS
    * RPC_SERVER_METRICS
    * MESSAGING_SPANS
    * DATABASE_CLIENT_SPANS
    * DATABASE_CLIENT_METRICS
    * DATABASE_POOL_METRICS
    * JVM_RUNTIME_METRICS
    * GRAPHQL_SERVER_SPANS
    * FAAS_SERVER_SPANS
    * GENAI_CLIENT_SPANS
    * GENAI_CLIENT_METRIC
* functions
  * The specific functionality that the instrumentation provides
  * Options are:
    * HTTP_ROUTE_ENRICHER
    * LIBRARY_DOMAIN_ENRICHER
    * EXPERIMENTAL_ONLY
    * CONTEXT_PROPAGATION
    * UPSTREAM_ADAPTER
    * CONFIGURATION
    * CONTROLLER_SPANS
    * VIEW_SPANS
    * SYSTEM_METRICS
* library_link
  * URL to the library or framework's main website or documentation, or if those don't exist, the
  GitHub repository.
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
* configuration settings
  * List of settings that are available for the instrumentation module
  * Each setting has a name, description, type, and default value
* metrics
  * List of metrics that the instrumentation module collects, including the metric name, description, type, and attributes.
  * Separate lists for the metrics emitted by default vs via configuration options.
* spans
  * List of spans kinds the instrumentation module generates, including the attributes and their types.
  * Separate lists for the spans emitted by default vs via configuration options.

## Methodology

### metadata.yaml file

Within each instrumentation source directory, a `metadata.yaml` file can be created to provide
additional information about the instrumentation module.

As of now, the following fields are supported, all of which are optional:

```yaml
description: "This instrumentation enables..."    # Description of the instrumentation module
semantic_conventions:                             # List of semantic conventions the instrumentation adheres to
  - HTTP_CLIENT_SPANS
  - DATABASE_CLIENT_SPANS
  - JVM_RUNTIME_METRICS
functions:                                        # List of functions this instrumentation provides
  - HTTP_ROUTE_ENRICHER
  - CONTEXT_PROPAGATION
disabled_by_default: true                         # Defaults to `false`
classification: internal                          # instrumentation classification: library | internal | custom
library_link: https://...                         # URL to the library or framework's main website or documentation
configurations:
  - name: otel.instrumentation.common.db-statement-sanitizer.enabled
    description: Enables statement sanitization for database queries.
    type: boolean               # boolean | string | list | map
    default: true
override_telemetry: false                         # Set to true to ignore auto-generated .telemetry files
additional_telemetry:                             # Manually document telemetry metadata
  - when: "default"
    metrics:
      - name: "metric.name"
        description: "Metric description"
        type: "COUNTER"
        unit: "1"
        attributes:
          - name: "attribute.name"
            type: "STRING"
    spans:
      - span_kind: "CLIENT"
        attributes:
          - name: "span.attribute"
            type: "STRING"
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

### Spans and Metrics

In order to identify what telemetry is emitted from instrumentations, we can hook into the
`InstrumentationTestRunner` class and collect the metrics and spans generated during runs. We can then
leverage the `afterTestClass()` in the Agent and library test runners to then write this information
into temporary files. When we analyze the instrumentation modules, we can read these files and
generate the telemetry section of the instrumentation-list.yaml file.

The data is written into a `.telemetry` directory in the root of each instrumentation module. This
data will be excluded from git and just generated on demand.

Each file has a `when` value along with the list of metrics that indicates whether the telemetry is
emitted by default or via a configuration option.

#### Manual Telemetry Documentation

In addition to auto-generated telemetry data from test runs, you can manually document telemetry
metadata directly in the `metadata.yaml` file. This is useful for:

- Documenting telemetry that may not be captured during test runs
- Overriding auto-generated telemetry data when it's incomplete or incorrect
- Adding additional telemetry documentation that complements the auto-generated data

You can add manual telemetry documentation using the `additional_telemetry` field:

```yaml
additional_telemetry:
  - when: "default"  # or any configuration condition
    metrics:
      - name: "my.custom.metric"
        description: "Description of the metric"
        type: "COUNTER"
        unit: "1"
        attributes:
          - name: "attribute.name"
            type: "STRING"
    spans:
      - span_kind: "CLIENT"
        attributes:
          - name: "span.attribute"
            type: "STRING"
```

To completely replace auto-generated telemetry data (ignoring `.telemetry` files), set `override_telemetry: true`:

```yaml
override_telemetry: true
additional_telemetry:
  - when: "default"
    metrics:
      - name: "documented.metric"
        description: "This replaces all auto-generated metrics"
        type: "GAUGE"
        unit: "ms"
```

When both manual and auto-generated telemetry exist for the same `when` condition, they are merged with manual entries taking precedence in case of conflicts (same metric name or span kind).

## Doc Synchronization

The documentation site has a section that lists all the instrumentations in the context of
documenting how to disable them.

We have a class `DocSynchronization` that runs a check against our instrumentation-list.yaml file to
identify when we have missing entries, so we know to go update them.

You can run this via:

`./gradlew :instrumentation-docs:docSiteAudit`

This is setup to run nightly in a github action.
