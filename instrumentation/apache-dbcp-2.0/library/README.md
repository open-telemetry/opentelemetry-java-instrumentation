# Library Instrumentation for Apache DBCP version 2.0 and higher

Provides OpenTelemetry instrumentation for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-apache-dbcp-2.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-apache-dbcp-2.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-apache-dbcp-2.0:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library allows registering `BasicDataSourceMXBean` instances for collecting
OpenTelemetry-based metrics. A non-null name of the data source must be explicitly provided.

```java
ApacheDbcpTelemetry apacheDbcpTelemetry;

void configure(OpenTelemetry openTelemetry, BasicDataSourceMXBean dataSource, String dataSourceName) {
  apacheDbcpTelemetry = ApacheDbcpTelemetry.create(openTelemetry);
  apacheDbcpTelemetry.registerMetrics(dataSource, dataSourceName);
}

void destroy(BasicDataSourceMXBean dataSource) {
  apacheDbcpTelemetry.unregisterMetrics(dataSource);
}
```
