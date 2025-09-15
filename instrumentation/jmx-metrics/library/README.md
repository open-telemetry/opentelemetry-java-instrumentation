# Library Instrumentation for JMX Metrics

Provides OpenTelemetry instrumentation for [Java Management Extensions (JMX)](https://docs.oracle.com/javase/tutorial/jmx/).

This instrumentation collects JMX-based metrics and exports them as OpenTelemetry metrics.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-jmx-metrics).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jmx-metrics</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-jmx-metrics:OPENTELEMETRY_VERSION")
```

### Usage

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jmx.engine.JmxMetricInsight;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

JmxMetricInsight jmxMetricInsight = JmxMetricInsight.createService(openTelemetry, 5000);

// Configure your JMX metrics
MetricConfiguration config = new MetricConfiguration();

jmxMetricInsight.startLocal(config);
```
