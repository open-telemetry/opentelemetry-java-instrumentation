# Library Instrumentation for Oracle UCP version 11.2 and higher

Provides OpenTelemetry instrumentation for [Oracle UCP](https://docs.oracle.com/database/121/JJUCP/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-oracle-ucp-11.2).

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

The single-argument `registerMetrics` method uses `UniversalConnectionPool.getName()` as the
OpenTelemetry metric pool name. To use an explicit metric pool name instead, without changing the
Oracle UCP pool name:

```java
void configureWithMetricPoolName(
    OpenTelemetry openTelemetry, UniversalConnectionPool universalConnectionPool) {
  OracleUcpTelemetry telemetry = OracleUcpTelemetry.create(openTelemetry);
  telemetry.registerMetrics(universalConnectionPool, "ordersPool");
}
```

Javaagent instrumentation derives the metric pool name from the `PoolDataSource` connection
properties and the JDBC URL, when present. Without a JDBC URL, it also uses the standard
`serverName`, `portNumber`, and `databaseName` values exposed directly by the `PoolDataSource`.
This applies only when the `PoolDataSource` does
not have an explicitly configured connection pool name. The derived format is
`host[:port][/database-or-service]`, or just `database-or-service` when no host is known. When the
connection information is unavailable, the fallback name is `oracle-ucp`. Pools connected to the
same database intentionally share the derived name, so
their asynchronous metric observations are aggregated under the same pool name.
