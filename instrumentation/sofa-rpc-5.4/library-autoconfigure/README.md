# Library Instrumentation for SOFARPC version 5.4 and higher

Provides automatic OpenTelemetry instrumentation for [SOFARPC](https://github.com/sofastack/sofa-rpc).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-sofa-rpc-5.4-autoconfigure).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-sofa-rpc-5.4-autoconfigure</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-sofa-rpc-5.4-autoconfigure:OPENTELEMETRY_VERSION")
```

### Usage

Register your SDK with `GlobalOpenTelemetry` before creating SOFARPC consumers or exporting
providers. SOFARPC loads the tracing filters automatically.

To configure the filters explicitly with your own `OpenTelemetry` instance, use the
[library with explicit registration](../library/README.md) instead.
