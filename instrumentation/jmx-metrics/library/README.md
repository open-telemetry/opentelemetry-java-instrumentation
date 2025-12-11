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
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;

import java.time.Duration;

// Get an OpenTelemetry instance
OpenTelemetry openTelemetry = ...;

JmxTelemetry jmxTelemetry = JmxTelemetry.builder(openTelemetry)
  // Configure included metrics (optional)
  .addRules(JmxTelemetry.class.getClassLoader().getResourceAsStream("jmx/rules/jetty.yaml"), "jetty")
  .addRules(JmxTelemetry.class.getClassLoader().getResourceAsStream("jmx/rules/tomcat.yaml"), "tomcat")
  // Configure custom metrics (optional)
  .addRules(Paths.get("/path/to/custom-jmx.yaml"))
  // delay bean discovery by 5 seconds
  .beanDiscoveryDelay(Duration.ofSeconds(5))
  .build();

jmxTelemetry.start();
```
