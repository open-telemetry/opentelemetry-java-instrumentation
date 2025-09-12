# Library Instrumentation for Vibur DBCP version 11.0 and higher

Provides OpenTelemetry instrumentation for [Vibur DBCP](https://www.vibur.org/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release]( https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-vibur-dbcp-11.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-vibur-dbcp-11.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-vibur-dbcp-11.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library allows registering `ViburDBCPDataSource` instances for collecting
OpenTelemetry-based metrics.

```java
ViburTelemetry viburTelemetry;

void configure(OpenTelemetry openTelemetry, ViburDBCPDataSource viburDataSource) {
  viburTelemetry = ViburTelemetry.create(openTelemetry);
  viburTelemetry.registerMetrics(viburDataSource);
}

void destroy(ViburDBCPDataSource viburDataSource) {
  viburTelemetry.unregisterMetrics(viburDataSource);
}
```
