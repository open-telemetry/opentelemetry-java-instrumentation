# Library Instrumentation for Apache DBCP versions 2.0+

Provides OpenTelemetry instrumentation for [Apache DBCP](https://commons.apache.org/proper/commons-dbcp/).

## Quickstart

### Add these dependencies to your project:

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-apache-dbcp-2.0).

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
