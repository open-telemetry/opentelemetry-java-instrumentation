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
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;

import java.nio.file.Paths;
import java.time.Duration;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

JmxTelemetry jmxTelemetry = JmxTelemetry.builder(openTelemetry)
  // Configure loading bundled metric definitions (optional)
  .setBundledMetricsSystemFilter(IncludeExclude.builder().setIncluded("*").build()) // load bundled definitions for all systems
  .setUnstableBundledMetricsFilter(IncludeExclude.builder().setIncluded("kafka.*").build()) // opt in to bundled unstable kafka.* metrics
  // Load metrics from classpath resource (optional)
  .addRules(JmxTelemetry.class.getClassLoader().getResourceAsStream("jmx/rules/tomcat.yaml"))
  // Load custom metrics by path (optional)
  .addRules(Paths.get("/path/to/custom-jmx.yaml"))
  // delay bean discovery by 5 seconds
  .beanDiscoveryDelay(Duration.ofSeconds(5))
  // filter captured metrics by their name (optional), will affect all loaded metric definitions
  .setMetrics(IncludeExclude.builder().setIncluded("tomcat.*", "jvm.*").setExcluded("kafka.connect.*").build())
  .build();

jmxTelemetry.start();
```

### Filters

| Filter | Description |
| --- | --- |
| `setBundledMetricsSystemFilter` | Systems whose bundled rules are loaded, such as `tomcat`. No bundled rules are loaded unless included or excluded patterns are configured. `*` selects all systems. |
| `setUnstableBundledMetricsFilter` | Unstable bundled metrics to enable for the selected systems, such as `kafka.*`. No unstable bundled metrics are enabled unless included or excluded patterns are configured. `*` selects all unstable bundled metrics. This filter never applies to custom rules, even when a custom metric has the same name as an unstable bundled metric. |
| `setMetrics` | Metrics to collect from both bundled and custom rules. All metrics are collected unless included or excluded patterns are configured. |

Matching is case-sensitive. `?` matches one character and `*` matches zero or more characters.
Excluded patterns take precedence over included patterns. If only excluded patterns are configured,
everything else is selected. An empty selector behaves the same as an unset filter.
