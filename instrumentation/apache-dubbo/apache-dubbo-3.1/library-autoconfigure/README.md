# Library Autoconfigure for Apache Dubbo version 3.1 and higher

Automatically instruments [Apache Dubbo](https://github.com/apache/dubbo).
The standalone library requires Dubbo 3.1 or later. The Java agent supports Dubbo 2.7 and later.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-dubbo-3.1-autoconfigure).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-dubbo-3.1-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-dubbo-3.1-autoconfigure:OPENTELEMETRY_VERSION")
```

### Usage

Register your SDK with `GlobalOpenTelemetry` before creating Dubbo consumers or exporting
services. Dubbo discovers and activates the tracing filters automatically.

To supply an `OpenTelemetry` instance per reference or service instead, use the
[manual instrumentation library](../library/README.md).
