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
disabled_by_default: true
classification: library
library_link: https://github.com/...
configurations:
  - name: otel.instrumentation.common.db-statement-sanitizer.enabled
    description: Enables statement sanitization for database queries.
    type: boolean
    default: true
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

All of our instrumentation modules are listed on the opentelemetry.io website in the context of how
to [suppress specific instrumentation](https://opentelemetry.io/docs/zero-code/java/agent/disable/#suppressing-specific-agent-instrumentation).

All new instrumentations should be added to this list. There is a
[Github action](../../.github/workflows/documentation-disable-list-audit.yml) that runs nightly to check
for any missing instrumentations, and will open an issue if any are found.
