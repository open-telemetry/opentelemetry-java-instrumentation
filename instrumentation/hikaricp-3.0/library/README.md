# Library Instrumentation for HikariCP version 3.0 and higher

Provides OpenTelemetry instrumentation for [HikariCP](https://github.com/brettwooldridge/HikariCP).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://mvnrepository.com/artifact/io.opentelemetry.instrumentation/opentelemetry-hikaricp-3.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-hikaricp-3.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-hikaricp-3.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a `MetricsTrackerFactory` implementation that can be added to
an instance of the `HikariConfig` (or `HikariDataSource`) to provide OpenTelemetry-based metrics.

```java
void configure(OpenTelemetry openTelemetry, HikariConfig connectionPoolConfig) {
  HikariTelemetry telemetry = HikariTelemetry.create(openTelemetry);
  connectionPoolConfig.setMetricsTrackerFactory(telemetry.createMetricsTrackerFactory());
}
```
