# Library Instrumentation for Apache Dubbo version 2.7 and higher

Provides OpenTelemetry instrumentation for [Apache Dubbo](https://github.com/apache/dubbo).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-dubbo-2.7).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-dubbo-2.7</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-dubbo-2.7:OPENTELEMETRY_VERSION")
```

### Usage

Register your SDK with `GlobalOpenTelemetry` before creating Dubbo consumers or exporting
services. Dubbo discovers and activates the tracing filters automatically.

To use a custom `OpenTelemetry` instance, create filters with
[`DubboTelemetry`](src/main/java/io/opentelemetry/instrumentation/apachedubbo/v2_7/DubboTelemetry.java).
Exclude the automatic filter on each side you configure with `-opentelemetryclient` or
`-opentelemetryserver` to avoid tracing the same call twice.
