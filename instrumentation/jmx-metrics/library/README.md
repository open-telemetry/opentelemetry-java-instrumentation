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
    // Configure loading embedded metric definitions (optional)
  .setInternalMetricsSystemFilter(IncludeExclude.builder().setIncluded("*").build()) // load internal metrics for all systems, this will load all internal stable metrics.
  .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().setIncluded("kafka.*").build()) // opt-in for all `kafka.*` internal unstable metrics
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

### Filter defaults

By default, the library loads no bundled JMX rules and applies no metric-name filter to custom
rules you add with `addRules(...)`. Leaving a filter unset and passing it an empty selector have
the same effect.

| Filter | Default | How to configure it |
| --- | --- | --- |
| `setInternalMetricsSystemFilter` | Loads no bundled rules. | Include system names such as `tomcat`, or use `setIncluded("*")` to select all systems. |
| `setInternalMetricsUnstableMetricsFilter` | Enables no unstable bundled metrics. | Include metric names or patterns such as `kafka.*`, or use `setIncluded("*")` to enable all unstable metrics for the selected systems. |
| `setMetrics` | Applies no metric-name restrictions. | Include or exclude metrics by name. This filter applies to both bundled and custom rules. |

The unstable-metrics filter never applies to custom rules, even when a custom metric has the same
name as an unstable bundled metric.

Matching is case-sensitive. `?` matches one character and `*` matches zero or more characters.
Excluded patterns take precedence over included patterns. If you supply only exclusions, everything
not excluded matches.
