# Manual Instrumentation for Oracle UCP

Provides OpenTelemetry instrumentation for [Oracle UCP](https://docs.oracle.com/database/121/JJUCP/).

## Quickstart

### Add these dependencies to your project:

Replace `OPENTELEMETRY_VERSION` with the latest stable
[release](https://mvnrepository.com/artifact/io.opentelemetry). `Minimum version: 1.15.0`

For Maven, add to your `pom.xml` dependencies:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-oracle-ucp-11.2</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-oracle-ucp-11.2:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library allows registering `UniversalConnectionPool` instances for collecting
OpenTelemetry-based metrics.

```java
OracleUcpTelemetry oracleUcpTelemetry;

void configure(OpenTelemetry openTelemetry, UniversalConnectionPool universalConnectionPool) {
  oracleUcpTelemetry = OracleUcpTelemetry.create(openTelemetry);
  oracleUcpTelemetry.registerMetrics(universalConnectionPool);
}

void destroy(UniversalConnectionPool universalConnectionPool) {
  oracleUcpTelemetry.unregisterMetrics(universalConnectionPool);
}
```
