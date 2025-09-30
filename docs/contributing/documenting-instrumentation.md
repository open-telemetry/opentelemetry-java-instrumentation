# Documenting Instrumentation

Due to the large number of instrumentations supported in this project, it is important to maintain a
consistent documentation approach. We use structured metadata files and README.md files along with
tooling and automation to both generate and audit documentation.

This document outlines the documentation aspirations for this project. Not all instrumentations
will meet all of these guidelines or already be documented in this way.

## README.md Files for Library Instrumentations

Every library instrumentation module must have a README.md file in the library directory root
(`instrumentation/{some instrumentation}/library/README.md`) that follows this pattern:

```markdown
# Library Instrumentation for [Technology] version [X.Y] and higher

Provides OpenTelemetry instrumentation for [Technology link].

[Any other relevant context about the instrumentation]

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](maven-central-link).

For Maven, add to your `pom.xml` dependencies:

    ```xml
    <dependencies>
      <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>{library artifact name}</artifactId>
        <version>OPENTELEMETRY_VERSION</version>
      </dependency>
    </dependencies>
    ```

For Gradle, add to your dependencies:

    ```kotlin
    implementation("io.opentelemetry.instrumentation:{library artifact name}:OPENTELEMETRY_VERSION")
    ```

### Usage

[Code examples showing integration]
```

Following these sections, you can include any other relevant information.

**If there is a difference in functionality between the library and javaagent instrumentation, it is
important to document these differences.**

## README.md Files for Javaagent Instrumentations

Every javaagent instrumentation module should have a README.md file in the javaagent directory root
(`instrumentation/{some instrumentation}/javaagent/README.md`) that follows this pattern:

```markdown
# [Technology] Instrumentation

[Brief description of what the instrumentation does and what versions it applies to]

## Settings
| System property | Type | Default | Description |
|-----------------|------|---------|-------------|
| `property.name` | Type | Default | Description |
```


**Note:** At some point we will likely automate the generation of this javaagent README.md file
using the metadata.yaml file described below.


## Metadata.yaml Files

Each instrumentation should have a `metadata.yaml` file in the root of the instrumentation directory
(`instrumentation/{some instrumentation}/metadata.yaml`) that contains structured metadata about the
instrumentation.

Example:

```yaml
description: "This instrumentation enables..."
semantic_conventions:
  - HTTP_CLIENT_SPANS
  - DATABASE_CLIENT_SPANS
  - JVM_RUNTIME_METRICS
disabled_by_default: true
classification: library
library_link: https://github.com/...
configurations:
  - name: otel.instrumentation.common.db-statement-sanitizer.enabled
    description: Enables statement sanitization for database queries.
    type: boolean
    default: true
override_telemetry: false
additional_telemetry:
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

### Description (required)

At a minimum, every instrumentation metadata file should include a `description`.

Some example descriptions:

* This instrumentation enables HTTP server spans and HTTP server metrics for the ActiveJ HTTP server.
* This instrumentation provides context propagation for Akka actors, it does not emit any telemetry
  on its own.
* The Alibaba Druid instrumentation generates database connection pool metrics for druid data sources.
* The Apache Dubbo instrumentation provides RPC client spans and RPC server spans for Apache Dubbo
  RPC calls. Each call produces a span named after the Dubbo method, enriched with standard RPC
  attributes (system, service, method), network attributes, and error details if an exception
  occurs.

Some notes when writing descriptions:

* You don't always need to explicitly name the instrumentation, and you can start with "This
  instrumentation..."
* Prefer the convention of using the word "enables" when describing what the instrumentation does,
  "This instrumentation **enables** HTTP server spans and HTTP server metrics for the ActiveJ" instead
  of something like "This instrumentation **provides** HTTP server spans and HTTP server metrics for the ActiveJ".
* Explicitly state whether the instrumentation generates new telemetry (spans, metrics, logs).
  * If it doesn't generate new telemetry, clearly explain what it's purpose is, for example whether it
    augments or enriches existing telemetry produced by other instrumentations (e.g., by adding
    attributes or ensuring context propagation).
* When describing the functionality of the instrumentation and the telemetry, specify using
  [semantic convention categories](https://opentelemetry.io/docs/specs/semconv/) when possible
  (e.g., "database client spans", "RPC server metrics", "consumer messaging spans").
* Do not include specific method names, class names, or other low-level implementation details in
  the description unless they are essential to understanding the purpose of the instrumentation.
* It is not usually necessary to include specific library or framework version numbers in the
  description, unless that context is significant in some way.


### Semantic Conventions

If the instrumentation adheres to one or more specific semantic conventions, include a
`semantic_conventions` field with a list of the relevant semantic convention categories.

List of possible options:

* [HTTP_CLIENT_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-spans.md#http-client-span)
* [HTTP_CLIENT_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#http-client)
* [HTTP_SERVER_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-spans.md#http-server)
* [HTTP_SERVER_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#http-server)
* [RPC_CLIENT_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-spans.md#rpc-client-span)
* [RPC_CLIENT_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#rpc-client)
* [RPC_SERVER_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-spans.md#rpc-server-span)
* [RPC_SERVER_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#rpc-server)
* [MESSAGING_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-spans.md)
* [DATABASE_CLIENT_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-spans.md)
* [DATABASE_CLIENT_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-metrics.md)
* [DATABASE_POOL_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-metrics.md)
* [JVM_RUNTIME_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md)
* [GRAPHQL_SERVER_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/graphql/graphql-spans.md)
* [FAAS_SERVER_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/faas/faas-spans.md)
* [GENAI_CLIENT_SPANS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-spans.md)
* [GENAI_CLIENT_METRICS](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-metrics.md#generative-ai-client-metrics)

### Library Link

For library instrumentations, include a `library_link` field with a URL to the library or framework's
main website or documentation, or if those don't exist, the GitHub repository.

### Configurations

If an instrumentation module has configuration options, they should be documented in the
`configurations` section of the `metadata.yaml` file.

Each configuration should include:

* `name`: The full configuration property name, for example `otel.instrumentation.common.db-statement-sanitizer.enabled`.
* `description`: A brief description of what the configuration does.
* `type`: The data type of the configuration value. Supported types are: `boolean`, `string`, `list`, and `map`.
* `default`: The default value for the configuration.


If a configuration enables experimental attributes, list them, for example:

> Enables experimental span attributes `couchbase.operation_id` and `couchbase.local.address`.


### Classification (optional)

If an instrumentation module does not specify a `classification`, it is assumed to be `library`.

There are currently three supported classifications:

* `library`: An instrumentation that provides automatic instrumentation for a specific library or
  framework. This is the default classification.
* `internal`: An instrumentation that is used internally by other instrumentations or the OpenTelemetry
  SDK itself, and is not intended for direct use by end users.
* `custom`: An instrumentation that is intended for custom or user-defined use cases, and may not
  fit into the standard library or internal categories.

The primary way this `classification` is used is to group and filter instrumentations by their
utility in tooling and documentation. If you are unsure which classification to use, you can omit
this field, and it will default to `library`.


### Disabled by Default (optional)

If an instrumentation is disabled by default, set `disabled_by_default: true`. This indicates that
the instrumentation will not be active unless explicitly enabled by the user. If this field is omitted,
it defaults to `false`, meaning the instrumentation is enabled by default.

### Manual Telemetry Documentation (optional)

You can manually document telemetry metadata (metrics and spans) directly in the `metadata.yaml` file
using the `additional_telemetry` field. This is useful for:

- Documenting telemetry that may not be captured during automated test runs
- Adding telemetry documentation when `.telemetry` files are not available
- Providing additional context or details about emitted telemetry

#### additional_telemetry

The `additional_telemetry` field allows you to specify telemetry metadata organized by configuration
conditions (`when` field):

```yaml
additional_telemetry:
  - when: "default"  # Telemetry emitted by default
    metrics:
      - name: "http.server.request.duration"
        description: "Duration of HTTP server requests"
        type: "HISTOGRAM"
        unit: "ms"
        attributes:
          - name: "http.method"
            type: "STRING"
          - name: "http.status_code"
            type: "LONG"
    spans:
      - span_kind: "SERVER"
        attributes:
          - name: "http.method"
            type: "STRING"
          - name: "http.url"
            type: "STRING"
  - when: "otel.instrumentation.example.experimental-metrics.enabled"  # Telemetry enabled by configuration
    metrics:
      - name: "example.experimental.metric"
        description: "Experimental metric enabled by configuration"
        type: "COUNTER"
        unit: "1"
```

Each telemetry entry includes:

- `when`: The configuration condition under which this telemetry is emitted. Use `"default"` for telemetry
  emitted by default, or specify the configuration option name for conditional telemetry.
- `metrics`: List of metrics with their name, description, type, unit, and attributes
- `spans`: List of span configurations with their span_kind and attributes

For metrics, supported `type` values include: `COUNTER`, `GAUGE`, `HISTOGRAM`, `EXPONENTIAL_HISTOGRAM`.

For spans, supported `span_kind` values include: `CLIENT`, `SERVER`, `PRODUCER`, `CONSUMER`, `INTERNAL`.

For attributes, supported `type` values include: `STRING`, `LONG`, `DOUBLE`, `BOOLEAN`.

#### override_telemetry

Set `override_telemetry: true` to completely replace any auto-generated telemetry data from `.telemetry`
files. When this is enabled, only the manually documented telemetry in `additional_telemetry` will be
used, and any `.telemetry` files will be ignored.

```yaml
override_telemetry: true
additional_telemetry:
  - when: "default"
    metrics:
      - name: "manually.documented.metric"
        description: "This completely replaces auto-generated telemetry"
        type: "GAUGE"
        unit: "bytes"
```

If `override_telemetry` is `false` or omitted (default behavior), manual telemetry will be merged with
auto-generated telemetry, with manual entries taking precedence in case of conflicts (same metric name
or span kind within the same `when` condition).

## Instrumentation List (docs/instrumentation-list.md)

The contents of the `metadata.yaml` files are combined with other information about the instrumentation
to generate a complete catalog of instrumentations in `docs/instrumentation-list.md`. This file
is generated via a gradle task and should not be edited directly (see
[this readme](../../instrumentation-docs/readme.md) for more information on this process).

**If you are submitting new instrumentation or updating existing instrumentation, you do not need to
update this file unless you want to, as it can take a significant amount of time to run** (40+
minutes). Each night a [GitHub Action](../../.github/workflows/metadata-update.yml) runs to
regenerate this file based on the current state of the repository, so all changes will be reflected
within 24 hours.

## opentelemetry.io

All of our instrumentation modules are listed on the opentelemetry.io website in two places:

### Supported Libraries

The [Supported Libraries](https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/)
page lists all the library instrumentations that are included in the OpenTelemetry Java agent. It
mostly mirrors the information from the [supported libraries](../supported-libraries.md) page in
this repo, and should be updated when adding or removing library instrumentations. There is a
[Github action](../../.github/workflows/documentation-synchronization-audit.yml) that runs nightly
to check for any missing instrumentations, and will open an issue if any are found.

This page may be automatically generated in the future, but for now it is manually maintained.

### Suppressing Instrumentation

The [Suppressing instrumentation](https://opentelemetry.io/docs/zero-code/java/agent/disable/#suppressing-specific-agent-instrumentation)
page lists the instrumentations in the context of the keys needed for using
the `otel.instrumentation.[name].enabled` configuration.

All new instrumentations should be added to this list. There is a
[Github action](../../.github/workflows/documentation-synchronization-audit.yml) that runs nightly to check
for any missing instrumentations, and will open an issue if any are found.
