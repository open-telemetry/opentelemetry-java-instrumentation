# Doc Generator

Runs analysis on instrumentation modules in order to generate documentation.

## How to use

Run the analysis to update the instrumentation-list.yaml:

`./gradlew :instrumentation-docs:runAnalysis`

### Telemetry collection

Collect default telemetry and supported non-default telemetry modes, such as experimental
attributes, optional metrics, and semantic convention selection.

| Test purpose                             | Collect telemetry? | `metadataConfig`                          |
| ---------------------------------------- | ------------------ | ----------------------------------------- |
| Document default telemetry               | Yes                | Unset                                     |
| Document supported non-default telemetry | Yes                | Relevant non-default `key=value` settings |
| Unit tests or regression-only variants   | No                 | Unset                                     |

For javaagent, plain-library, and library-autoconfigure tests, baseline fixtures may capture
synthetic HTTP and messaging headers. The capture may come from shared test exporter configuration,
a library builder, or production properties that the integration actually reads. Do not require
empty overrides, capture-only flags or variants, or `metadataConfig` conditions solely for this
fixture setup. Other non-default telemetry settings remain subject to the normal profile rules. Test
opt-in telemetry in named variants wired into `check`. Preserve normal suite coverage; filtered
metadata-collection tests are not a substitute.

| Property | Synthetic selector |
| -------- | ------------------ |
| `otel.instrumentation.http.client.request-captured-headers` | `X-Test-Request` |
| `otel.instrumentation.http.client.response-captured-headers` | `X-Test-Response` |
| `otel.instrumentation.http.server.request-captured-headers` | `X-Test-Request` |
| `otel.instrumentation.http.server.response-captured-headers` | `X-Test-Response` |
| `otel.instrumentation.common.messaging.headers.included` | `Test-Message-Header`, `Test-Message-Int-Header`, `Test_Message_Header`, `Test_Message_Int_Header` |

These synthetic selectors are fixture values, not production defaults. Their attributes may appear
in raw `.telemetry` files under `when: default`, but the analyzer excludes them from generated
telemetry inventories.

For example, a plain-library fixture can configure synthetic messaging-header capture in its baseline
builder:

```java
AwsSdkTelemetry.builder(testing.getOpenTelemetry())
    .setHeaders(IncludeExclude.builder().setIncluded("Test-Message-*").build())
    .build();
```

A variant enabling experimental attributes still labels only that meaningful opt-in:
`AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)`. It does not label the synthetic
header selector or its test-only flag.

`metadataConfig` labels output; it does not configure instrumentation or enable collection.
An absent or empty label records telemetry under `when: default`. Default means instrumentation
defaults, not the task name or settings on `test`; synthetic header capture is excluded from the
label for every fixture type. The generator merges telemetry with the same label.
Include only non-default user-facing settings needed to describe this instrumentation's signals,
span kinds, attributes, metrics, or events, including inherited, fixture, and library-builder settings,
except for synthetic header capture. For settings applied through JVM properties, use flat
`key=value` conditions without `-D`. For programmatically configured library profiles, use the actual
builder calls as labels (for example,
`AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)`) instead of javaagent properties
that do not configure the library builder. Separate multiple conditions or builder calls with commas,
using consistent spelling and ordering for the same mode. Split or filter collected tasks that would
mix profiles under one label. Apply settings separately through JVM properties or library APIs.
Label non-default metric selectors, not Java source-set names; document runtime prerequisites separately.

Omit test setup, such as ports, timeouts, JVM access flags, and unrelated instrumentation settings.
Enabling a whole default-off instrumentation is also setup; `disabled_by_default` records that
requirement. Include span suppression settings only when they determine this instrumentation's
telemetry, not merely spans from other instrumentation in the test trace.

Shared test conventions forward `-PcollectMetadata=true` to test JVMs.
Register representative tasks in
`.github/scripts/instrumentations.sh` covering each profile's instrumentation scope and telemetry
shape; equivalent suites need not all collect. Exclude unit tests and regression-only
disablement, adapter-fallback, or classpath compatibility variants based on their purpose, not their
names. A full-suite `testReceiveSpansDisabled` may document default telemetry; a suppression-only
regression task does not. Keep regression tasks in `check`, but omit them from the collection script.
Keep `metadataConfig` labels on the tasks that document non-default telemetry.

When a feature supports distinct telemetry emission modes, collect its DEFAULT profile and
each supported non-default profile in separate test JVMs. The following schematic example adds
a PREVIEW profile without changing the default test JVM. Substitute the feature's actual supported
property and value for `<feature-mode-property>` and `<preview-mode-value>`, and choose a task name
appropriate to the module; `examplePreviewTest` is illustrative, not a task available in every module.
Use additional variants for combined emission only when the feature supports it. Keep variants
scoped to suites exercising the feature and preserve the source suite's JVM settings.

```kotlin
tasks {
  val examplePreviewTest = register<Test>("examplePreviewTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("<feature-mode-property>", "<preview-mode-value>")
    systemProperty("metadataConfig", "<feature-mode-property>=<preview-mode-value>")
  }

  check {
    dependsOn(examplePreviewTest)
  }
}
```

Generate `.telemetry` files with the selected tasks, then run the analyzer. Running `test` alone
does not execute a separately registered variant:

```bash
./gradlew :instrumentation:<module>:javaagent:test :instrumentation:<module>:javaagent:<additional-mode-task> -PcollectMetadata=true
./gradlew :instrumentation-docs:runAnalysis
```

Replace `<module>` with the instrumentation's Gradle project path and `<additional-mode-task>`
with the actual registered task for the supported additional mode.

Or use the same collector as daily CI to run all tasks registered for metadata collection:

```bash
./instrumentation-docs/collect.sh
```

The collector clears stale `.telemetry` directories only in selected modules, once before
collection, and preserves profiles across all passes. Latest-dependency tasks run separately with
`-PtestLatestDeps=true`. Set `DEBUG_LOGGING=true` for full Gradle output.

On ARM (`arm64` or `aarch64`), the suites in `COLIMA_INSTRUMENTATIONS` require x86_64 containers.
Install Colima and start a dedicated profile before collecting:

```bash
colima start --profile otel-metadata --arch x86_64 --memory 4 --activate=false
./instrumentation-docs/collect.sh
```

The other suites use your normal Docker environment. Only the x86-only pass uses the
`otel-metadata` Docker socket. The collector does not start or stop Colima profiles; when finished,
you can stop this profile with `colima stop --profile otel-metadata`. On x86_64, Colima is not used.
Missing prerequisites and failed collection tasks cause the collector to exit unsuccessfully.

## Instrumentation Hierarchy

The repository contains flat modules, version groups, and nested framework groups. For example:

```text
instrumentation/
├── grpc-1.6/
├── netty/
│   ├── netty-3.8/
│   └── netty-4.1/
└── spring/
    └── spring-webmvc/
        ├── spring-webmvc-3.1/
        └── spring-webmvc-6.0/
```

The doc generator uses the full module-directory name as the module's name. Namespace and group
currently both use the prefix before the first hyphen in that name, regardless of directory nesting:

| Module name         | Namespace | Group    |
| ------------------- | --------- | -------- |
| `grpc-1.6`          | `grpc`    | `grpc`   |
| `netty-4.1`         | `netty`   | `netty`  |
| `spring-webmvc-3.1` | `spring`  | `spring` |

Namespace and group are documentation groupings, not javaagent enablement selectors.
The names registered by a javaagent `InstrumentationModule` determine its selectors. Use the full
module-directory name as the primary, followed by a versionless secondary:

```java
public SpringWebMvcInstrumentationModule() {
  super("spring-webmvc-3.1", new String[] {"spring-webmvc"});
}
```

## Instrumentation metadata

- classification
  - `library` - Instrumentation that targets a library
  - `internal` - Instrumentation that is used internally by the OpenTelemetry Java Agent
  - `custom` - Utilities that are used to create custom instrumentation
- name
  - Identifier derived from the full instrumentation module-directory name
- semantic_conventions
  - The semantic conventions that the instrumentation module adheres to
  - Options are:
    - HTTP_CLIENT_SPANS
    - HTTP_CLIENT_METRICS
    - HTTP_SERVER_SPANS
    - HTTP_SERVER_METRICS
    - RPC_CLIENT_SPANS
    - RPC_CLIENT_METRICS
    - RPC_SERVER_SPANS
    - RPC_SERVER_METRICS
    - MESSAGING_SPANS
    - DATABASE_CLIENT_SPANS
    - DATABASE_CLIENT_METRICS
    - DATABASE_POOL_METRICS
    - JVM_RUNTIME_METRICS
    - GRAPHQL_SERVER_SPANS
    - FAAS_SERVER_SPANS
    - GENAI_CLIENT_SPANS
    - GENAI_CLIENT_METRIC
- features
  - The specific functionality that the instrumentation provides
  - Options are:
    - HTTP_ROUTE
    - CONTEXT_PROPAGATION
    - AUTO_INSTRUMENTATION_SHIM
    - CONTROLLER_SPANS
    - VIEW_SPANS
    - LOGGING_BRIDGE
    - RESOURCE_DETECTOR
- library_link
  - URL to the library or framework's main website or documentation, or if those don't exist, the
    GitHub repository.
- source_path
  - Path to the source code of the instrumentation module
- minimum_java_version
  - Minimum Java version required by the instrumentation module. If not specified, it is assumed to
    be Java 8
- description
  - Short description of what the instrumentation does
- has_standalone_library
  - Whether the instrumentation module has a standalone library that can be used outside of the agent
- has_javaagent
  - Whether the instrumentation module has a javaagent component that can be used with the OpenTelemetry Java Agent
- target_versions
  - List of supported versions by the module, broken down by `library` or `javaagent` support
- scope (See [instrumentation-scope](https://opentelemetry.io/docs/specs/otel/common/instrumentation-scope/)
  docs)
  - name: The scope name of the instrumentation, `io.opentelemetry.{instrumentation name}`
  - schema_url: Location of the telemetry schema that the instrumentation’s emitted telemetry
    conforms to. (See [telemetry schema docs](https://opentelemetry.io/docs/specs/otel/schemas/#schema-url))
  - attributes: The instrumentation scope’s optional attributes provide additional information
    about the scope.
- configuration_refs
  - List of configuration definition ids the instrumentation module exposes.
  - Each id resolves to an entry in the top-level `definitions.configurations` catalog (see below).
  - This is the _generated_ representation. In a module's `metadata.yaml` you still author each
    configuration either inline or as a shared `ref` (see "metadata.yaml file" and "Shared
    configuration definitions" below); the generator collects both forms into the catalog and emits
    the ids here.
- metrics (referenced via `metric_refs`)
  - Each telemetry `when` block lists `metric_refs`: the ids of metric definitions the module emits.
  - Each id resolves to an entry in the top-level `definitions.metrics` catalog.
  - Separate `when` blocks distinguish metrics emitted by default vs via configuration options.
  - This is the _generated_ representation, produced automatically from collected telemetry (and any
    inline `additional_telemetry`). Metrics are always authored inline (or auto-collected from
    tests) — there is no author-time shared metric catalog; the generator does the de-duplication
    into `definitions.metrics` for you.
- spans
  - List of span kinds the instrumentation module generates, including the attributes and their types.
  - Emitted inline under each telemetry `when` block (spans are not yet part of the definitions catalog).
  - Separate `when` blocks distinguish spans emitted by default vs via configuration options.
- events (referenced via `event_refs`)
  - Each telemetry `when` block lists `event_refs`: the ids of event definitions the module emits.
  - Each id resolves to an entry in the top-level `definitions.events` catalog.
  - An event is a log record carrying an event name, for example the GenAI message events or the
    exception events emitted when `otel.semconv.exception.signal.preview=logs` is set.
  - Each definition records the event `name`, its `severity` (omitted when the instrumentation does
    not set one) and its attributes.
  - This is the _generated_ representation, produced automatically from collected telemetry. There is
    no author-time catalog and no manual authoring path for events.

### Definitions catalog

To avoid inlining the same metric or configuration block into every instrumentation entry, common
definitions are collected once into a top-level `definitions` section and referenced by id:

```yaml
definitions:
  configurations:
    http.known-methods:          # id, reused as the configuration_refs value
      name: otel.instrumentation.http.known-methods
      declarative_name: java.common.http.known_methods
      description: ...
      type: list
      default: CONNECT,DELETE,GET,HEAD,OPTIONS,PATCH,POST,PUT,TRACE
  metrics:
    http.client.request.duration-1a2b3c4d:   # <metric name>-<short content hash>
      name: http.client.request.duration
      description: Duration of HTTP client requests.
      instrument: histogram
      data_type: HISTOGRAM
      unit: s
      attributes: [...]
  events:
    db.client.operation.exception-1a2b3c4d:  # <event name>-<short content hash>
      name: db.client.operation.exception
      severity: WARN
      attributes: [...]
libraries:
- name: java-http-client
  configuration_refs:
  - http.known-methods
  telemetry:
  - when: default
    metric_refs:
    - http.client.request.duration-1a2b3c4d
    event_refs:
    - db.client.operation.exception-1a2b3c4d
```

Configuration ids are the curated ids from the shared registry (see below) for shared options, or the
config `name` for module-specific options. If two module-specific options share the same `name` but
have different content, their ids are disambiguated as `<config name>-<short content hash>`. Metric
ids are `<metric name>-<short content hash>`; the
hash disambiguates metrics that share a name but have different attribute sets. Any consumer of
`instrumentation-list.yaml` must resolve `configuration_refs` / `metric_refs` against `definitions`
before reading the underlying config/metric content.

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
features:                                        # List of features this instrumentation provides
  - HTTP_ROUTE
  - CONTEXT_PROPAGATION
disabled_by_default: true                         # Defaults to `false`
classification: internal                          # instrumentation classification: library | internal | custom
library_link: https://...                         # URL to the library or framework's main website or documentation
configurations:
  # Do not add a general enabled/disabled config for the whole module here (for example
  # `otel.instrumentation.my-module.enabled`) — modules are assumed enabled unless
  # `disabled_by_default: true` is set above.
  # Reference a shared definition instead of hand-copying a common config block. The id must exist
  # in instrumentation-docs/src/main/resources/shared-config-definitions.yaml (an unknown ref fails
  # the build). See "Shared configuration definitions" below.
  - ref: common.db.query-sanitization.enabled
  # Module-specific configs are still defined inline:
  - name: otel.instrumentation.my-module.some-feature
    declarative_name: java.my_module.some_feature    # Optional: YAML config path
    description: Enables the some-feature capability.
    type: boolean               # boolean | string | list | map (the flat form)
    default: true
    examples:                   # Optional: Example values for this configuration
      - "true"
      - "false"
  # Structured-list config: a list of objects in declarative config. `name` may be omitted for
  # declarative-only settings (identified solely by `declarative_name`).
  - declarative_name: java.common.http.client.url_template_rules
    description: Rules for deriving low-cardinality URL templates from HTTP client request URLs.
    type: list
    default: ""
    declarative_type: structured_list      # Optional: overrides the declarative-form shape
    declarative_schema:                     # Required when declarative_type is structured_list
      type: object
      required: [pattern, template]
      properties:
        pattern:
          type: string
          description: Regular expression matched against the request URL.
          example: '/users/\d+'
        template:
          type: string
          description: Template used to derive the low-cardinality route.
          example: '/users/{id}'
        override:
          type: boolean
          default: false
          description: Whether this rule overrides an already-applied template.
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

### Shared configuration definitions

Many instrumentations expose the same configuration options (for example `http.known-methods` or
`common.peer-service-mapping`). Instead of hand-copying an identical block into every `metadata.yaml`,
these are defined once in
[`src/main/resources/shared-config-definitions.yaml`](src/main/resources/shared-config-definitions.yaml)
under a stable id, and each module references them:

```yaml
configurations:
  - ref: http.known-methods
  - ref: common.peer-service-mapping
```

At metadata-parse time (`SharedConfigurationRegistry`) each `ref` is expanded into the full
configuration option; an unknown id fails the build. The same ids are reused as the keys in the
generated `definitions.configurations` catalog, so editing a description in the registry once updates
every module that references it.

To add a new shared option, add an entry to `shared-config-definitions.yaml` and reference it by id.
To change an option for a single module only, define it inline instead of using a `ref`.

### Gradle File Derived Information

We parse gradle files in order to determine several pieces of metadata:

- Javaagent versions are determined by the `muzzle` plugin configurations
- Standalone Library versions are identified, but we do not try and parse version ranges
- Minimum Java version is determined by the `otelJava` configurations

#### Excluding a muzzle directive from the docs

Every `pass { ... }` block contributes a line to `javaagent_target_versions`. Some pass blocks exist
only to verify a sub-range or an alternate dependency set, and publishing their version range
alongside the module's real range is misleading. Add a `// instrumentation-docs:ignore` comment
inside such a block to leave it out of the generated documentation:

```kotlin
muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,3)")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the [2,3) directive above is the range we document
    name.set("Pre-2.6 network instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,2.6)")
  }
}
```

The comment must appear inside the `pass` block; placing it first is recommended. A comment above
`pass {` is not honored.
The marker has no effect on muzzle itself, only on the generated `instrumentation-list.yaml`.

### Scope

For now, the scope name is the only value that is implemented in our instrumentations. The scope
name is determined by the instrumentation module name: `io.opentelemetry.{instrumentation name}`

We will implement gatherers for the schemaUrl and scope attributes when instrumentations start
implementing them.

### Spans, Metrics and Events

In order to identify what telemetry is emitted from instrumentations, we can hook into the
`InstrumentationTestRunner` class and collect the metrics, spans and events generated during runs. We
can then leverage the `afterTestClass()` in the Agent and library test runners to then write this
information into temporary files. When we analyze the instrumentation modules, we can read these
files and generate the telemetry section of the instrumentation-list.yaml file.

The data is written into a `.telemetry` directory in the root of each instrumentation module, as
`metrics-*.yaml`, `spans-*.yaml`, `events-*.yaml` and `scope-*.yaml`. This data will be excluded from
git and just generated on demand.

Each file has a `when` value along with the list of metrics that indicates whether the telemetry is
emitted by default or via a configuration option.

Spans and metrics are collected inside the `waitAndAssertTraces` / `waitAndAssertMetrics` assertion
helpers. A successful metric assertion collects all currently exported metrics, accumulating the
union of attribute names and types across every data point and collected snapshot for each scope
and metric name. Tests should wait for operations with distinct attribute sets before completing
their assertions so that those attributes are observed. Events are instead swept up after
every test, so they are captured regardless of how the test asserted on them. A log record counts as
an event when it carries an event name, set either via `LogRecordBuilder.setEventName(...)` or via an
`event.name` attribute; ordinary log records, such as those produced by the logging library bridges,
are ignored.

An event is keyed by its name together with its severity, so an instrumentation that emits the same
event at two severities is documented as two shapes. The default `exception` event does exactly
this: `ERROR` for server and consumer operations, `WARN` for client and producer ones.

Telemetry only reaches the generated list for test tasks listed in
`.github/scripts/instrumentations.sh` during automated collection. The analyzer also reads
`.telemetry` files generated by local runs. Choose tasks and labels according to the
[telemetry collection convention](#telemetry-collection); a missing label records telemetry
under `when: default`, including for the shared synthetic header capture above. Label other
non-default telemetry options accordingly.
The exception events, for example, are collected by the
`testExceptionSignalLogs` tasks, which set
`metadataConfig` to `otel.semconv.exception.signal.preview=logs`.

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
