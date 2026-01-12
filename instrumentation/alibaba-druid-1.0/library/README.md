# Library Instrumentation for Alibaba Druid version 1.0 and higher

Provides OpenTelemetry instrumentation for [Alibaba Druid](https://github.com/alibaba/druid),
enabling database connection pool metrics for druid data sources.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-alibaba-druid-1.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-alibaba-druid-1.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-alibaba-druid-1.0:OPENTELEMETRY_VERSION")
```

### Usage

```java
import com.alibaba.druid.pool.DruidDataSource;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.alibabadruid.v1_0.DruidTelemetry;

// ...

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

// Create a DruidTelemetry instance
DruidTelemetry druidTelemetry = DruidTelemetry.create(openTelemetry);

// Create a DruidDataSource
DruidDataSource dataSource = new DruidDataSource();
// ... configure the dataSource

// Register the dataSource for metrics
druidTelemetry.registerMetrics(dataSource, "my-druid-pool");

// Unregister the dataSource when it's no longer needed
druidTelemetry.unregisterMetrics(dataSource);
```
