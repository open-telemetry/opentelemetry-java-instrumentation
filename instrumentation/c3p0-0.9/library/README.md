# Library Instrumentation for C3P0 version 0.9 and higher

Provides OpenTelemetry instrumentation for [C3P0](https://www.mchange.com/projects/c3p0/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release]( https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-c3p0-0.9).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-c3p0-0.9</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-c3p0-0.9:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library allows registering `PooledDataSource` instances for
collecting OpenTelemetry-based metrics.

```java
C3p0Telemetry c3p0Telemetry;

void configure(OpenTelemetry openTelemetry, PooledDataSource dataSource) {
  c3p0Telemetry = C3p0Telemetry.create(openTelemetry);
  c3p0Telemetry.registerMetrics(dataSource);
}

void destroy(PooledDataSource dataSource) {
  c3p0Telemetry.unregisterMetrics(dataSource);
}
```
