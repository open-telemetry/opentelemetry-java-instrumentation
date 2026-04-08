# JVM Runtime Metrics

> **Deprecated:** This module is deprecated. Use
> [`opentelemetry-runtime-telemetry`](../library/README.md) instead, which provides a unified API
> for all Java versions.

This module provides JVM runtime metrics as documented in the [semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release]( https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-runtime-telemetry-java8).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-runtime-telemetry-java8</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-runtime-telemetry-java8:OPENTELEMETRY_VERSION")
```

### Usage

Register JVM runtime metrics:

```java
OpenTelemetry openTelemetry = // OpenTelemetry instance configured elsewhere

RuntimeMetrics runtimeMetrics = RuntimeMetrics.create(openTelemetry);

// When done, close to stop metric collection
runtimeMetrics.close();
```

To select specific metrics, configure [metric views](https://opentelemetry.io/docs/languages/java/sdk/#views)
on the SDK to filter or customize which metrics are exported.

For example, using [declarative configuration](https://github.com/open-telemetry/opentelemetry-java-examples/tree/main/declarative-configuration):

```yaml
meter_provider:
  views:
    # Drop jvm.memory.committed metric
    - selector:
        instrument_name: jvm.memory.committed
      stream:
        aggregation:
          drop:
    # Only retain jvm.memory.type attribute on jvm.memory.used
    - selector:
        instrument_name: jvm.memory.used
      stream:
        attribute_keys:
          included:
            - jvm.memory.type
```

To retain only `jvm.memory.used` and drop all other JVM runtime metrics:

```yaml
meter_provider:
  views:
    # Drop all metrics from this instrumentation scope
    - selector:
        meter_name: io.opentelemetry.runtime-telemetry-java8
      stream:
        aggregation:
          drop:
    # Keep jvm.memory.used (views are additive, this creates a second stream)
    - selector:
        meter_name: io.opentelemetry.runtime-telemetry-java8
        instrument_name: jvm.memory.used
      stream: {}
```

## Garbage Collector Dependent Metrics

The attributes reported on memory metrics (`jvm.memory.*`) and GC metrics (`jvm.gc.*`) depend on
the garbage collector used by the application. See the
[runtime-telemetry library README](../../library/README.md#garbage-collector-dependent-metrics)
for details on attributes for various garbage collectors.
