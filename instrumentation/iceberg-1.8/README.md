# Library Instrumentation for Apache Iceberg Version 1.8 and Higher

Provides OpenTelemetry instrumentation for [Apache Iceberg](https://iceberg.apache.org/).

## Quickstart

### Add These Dependencies to Your Project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-iceberg-1.8).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-iceberg-1.8</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-iceberg-1.8:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library allows creating instrumented `Scan` (e.g., `TableScan`) instances for collecting and reporting OpenTelemetry-based sacn metrics. For example:

```java
OpenTelemetry openTelemetry = // ...
IcebergTelemetry icebergTelemetry = IcebergTelemetry.create(openTelemetry);
TableScan tableScan = icebergTelemetry.wrapScan(table.newScan());

try (CloseableIterable<FileScanTask> fileScanTasks = tableScan.planFiles()) {
  // Process the scan tasks
}

// The metrics will be reported after the scan tasks iterable is closed
```
