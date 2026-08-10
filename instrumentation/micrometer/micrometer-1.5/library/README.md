# Micrometer Instrumentation for Micrometer version 1.5 and higher

This module provides a [Micrometer registry](https://docs.micrometer.io/micrometer/reference/concepts/registry.html) which
sends Micrometer metrics to the
[OpenTelemetry Metrics SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/metrics).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-micrometer-1.5).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-micrometer-1.5</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-micrometer-1.5:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides an implementation of `MeterRegistry` to bridge Micrometer API to OpenTelemetry Metrics.

```java
MeterRegistry meterRegistry = OpenTelemetryMeterRegistry.builder(openTelemetry).build();
```

## How Micrometer instruments are mapped to OpenTelemetry

`<name>` below is the Micrometer meter name after the registry's
[naming convention](https://docs.micrometer.io/micrometer/reference/concepts/naming.html) has been
applied. The default convention passes the name through unchanged; see
[Prometheus mode](#prometheus-mode) for the exception.

| Micrometer instrument | OpenTelemetry instrument(s)                                            | Name(s)                            | Unit                           |
| --------------------- | ---------------------------------------------------------------------- | ---------------------------------- | ------------------------------ |
| `Counter`             | synchronous double counter                                             | `<name>`                           | base unit                      |
| `Gauge`               | asynchronous double gauge                                              | `<name>`                           | base unit                      |
| `Timer`               | synchronous double histogram, plus an asynchronous double gauge        | `<name>`, `<name>.max`             | base time unit                 |
| `DistributionSummary` | synchronous double histogram, plus an asynchronous double gauge        | `<name>`, `<name>.max`             | base unit                      |
| `LongTaskTimer`       | asynchronous long up-down counter, asynchronous double up-down counter | `<name>.active`, `<name>.duration` | `{tasks}`, base time unit      |
| `FunctionCounter`     | asynchronous double counter                                            | `<name>`                           | base unit                      |
| `FunctionTimer`       | asynchronous long counter, asynchronous double counter                 | `<name>.count`, `<name>.sum`       | `{invocation}`, base time unit |
| `Meter` (custom)      | one instrument per `Measurement`, see below                            | `<name>.<statistic>`               | base unit                      |

Notes:

- Micrometer tags become OpenTelemetry attributes. Meter names and tag keys both pass through the
  registry's naming convention, and metrics are emitted under the instrumentation scope
  `io.opentelemetry.micrometer-1.5`.
- Base units are used verbatim rather than translated to [UCUM](https://ucum.org/), so a base unit
  of `bytes` stays `bytes` rather than becoming `By`. The base time unit defaults to seconds and can
  be changed with `OpenTelemetryMeterRegistryBuilder.setBaseTimeUnit(TimeUnit)`.
- Only Micrometer's service level objectives become explicit bucket boundaries advice;
  `publishPercentileHistogram()`, `minimumExpectedValue`, and `maximumExpectedValue` do not.
  Micrometer percentiles cannot be represented as an OpenTelemetry histogram, but percentiles and
  service level objectives can additionally be emitted as `<name>.percentile` and
  `<name>.histogram` gauges by enabling the experimental
  `Experimental#setMicrometerHistogramGaugesEnabled`.
- Descriptions are deduplicated by instrument name within each registry, because in OpenTelemetry
  the description is one of an instrument's identifying fields. The first description registered
  for a name in that registry wins.
- The `<name>.max` gauge is deprecated and will be removed in 3.0. It is no longer emitted when
  `otel.instrumentation.common.v3-preview` is enabled.
- For a custom `Meter`, each measurement's `Statistic` determines the instrument type, and the name
  suffix is the statistic's Micrometer tag value, except for `TOTAL_TIME`, which uses `total_time`
  so that it does not clash with `TOTAL`. Before `otel.instrumentation.common.v3-preview` is
  enabled, the naming convention is applied to the combined name and suffix instead of to the name
  alone, so in [Prometheus mode](#prometheus-mode) a custom `Meter` of type `COUNTER` named
  `my.meter` with the base unit `bytes` is emitted as `my.meter.count.bytes` rather than
  `my.meter.bytes.count`.

### Prometheus mode

Prometheus mode approximates the naming behavior of Micrometer's `PrometheusMeterRegistry`, for
users exporting with the Prometheus exporter. It forces the base time unit to seconds and appends
the unit to the instrument name, so a `Timer` named `my.timer` becomes `my.timer.seconds` and a
`DistributionSummary` named `my.summary` with the base unit `bytes` becomes `my.summary.bytes`.
Encoding the unit in the metric name is contrary to the OpenTelemetry naming rules, so this mode is
disabled by default and should only be enabled for Prometheus compatibility.

```java
MeterRegistry meterRegistry =
    OpenTelemetryMeterRegistry.builder(openTelemetry).setPrometheusMode(true).build();
```
