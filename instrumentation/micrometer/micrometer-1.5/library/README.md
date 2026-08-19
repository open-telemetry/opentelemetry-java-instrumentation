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
[Prometheus mode](#prometheus-mode) for the exception. For [custom meters](#custom-meters), unless
`otel.instrumentation.common.v3-preview` is enabled, the naming convention is applied after the
statistic suffix is appended to the raw meter name. Their emitted names may therefore differ from
the `<name>.<statistic>` form under a custom convention.

| Micrometer instrument | OpenTelemetry instrument(s)                                            | Name(s)                            | Unit                           |
| --------------------- | ---------------------------------------------------------------------- | ---------------------------------- | ------------------------------ |
| `Counter`             | synchronous double counter                                             | `<name>`                           | base unit                      |
| `Gauge`               | asynchronous double gauge                                              | `<name>`                           | base unit                      |
| `Timer`               | synchronous double histogram, plus an asynchronous double gauge        | `<name>`, `<name>.max`             | base time unit                 |
| `DistributionSummary` | synchronous double histogram, plus an asynchronous double gauge        | `<name>`, `<name>.max`             | base unit                      |
| `LongTaskTimer`       | asynchronous long up-down counter, asynchronous double up-down counter | `<name>.active`, `<name>.duration` | `{tasks}`, base time unit      |
| `FunctionCounter`     | asynchronous double counter                                            | `<name>`                           | base unit                      |
| `FunctionTimer`       | asynchronous long counter, asynchronous double counter                 | `<name>.count`, `<name>.sum`       | `{invocation}`, base time unit |
| `Meter` (custom)      | one instrument per `Measurement`, see [below](#custom-meters)          | `<name>.<statistic>`               | base unit                      |

### Names and attributes

Micrometer tags become OpenTelemetry attributes. Meter names, tag keys, and tag values all pass
through the registry's naming convention, and metrics are emitted under the instrumentation scope
`io.opentelemetry.micrometer-1.5`.

The `<name>.max` gauge is deprecated and will be removed in 3.0. It is no longer emitted when
`otel.instrumentation.common.v3-preview` is enabled.

### Units

Base units are used verbatim rather than translated to [UCUM](https://ucum.org/), so a base unit of
`bytes` stays `bytes` rather than becoming `By`. The base time unit defaults to seconds and can be
changed with `OpenTelemetryMeterRegistryBuilder.setBaseTimeUnit(TimeUnit)`.

### Descriptions

Descriptions are deduplicated by instrument name within each registry, because in OpenTelemetry the
description is one of an instrument's identifying fields. The first description registered for a
name in that registry wins.

### Histograms, percentiles, and service level objectives

Only Micrometer's service level objectives become explicit bucket boundaries advice;
`publishPercentileHistogram()`, `minimumExpectedValue`, and `maximumExpectedValue` do not.

Micrometer percentiles cannot be represented as an OpenTelemetry histogram, but percentiles and
service level objectives can additionally be emitted as `<name>.percentile` and `<name>.histogram`
gauges by enabling the experimental `Experimental#setMicrometerHistogramGaugesEnabled`.

That setting does not apply to the `LongTaskTimer`, which is not bridged to an OpenTelemetry
histogram at all; its configured percentiles, percentile-histogram buckets, and service level
objectives are always emitted as gauges.

### Custom meters

For a custom `Meter`, each measurement's `Statistic` determines the instrument type and the name:

| `Statistic`    | OpenTelemetry instrument            | Name                |
| -------------- | ----------------------------------- | ------------------- |
| `COUNT`        | asynchronous double counter         | `<name>.count`      |
| `TOTAL`        | asynchronous double counter         | `<name>.total`      |
| `TOTAL_TIME`   | asynchronous double counter         | `<name>.total_time` |
| `ACTIVE_TASKS` | asynchronous double up-down counter | `<name>.active`     |
| `DURATION`     | asynchronous double gauge           | `<name>.duration`   |
| `MAX`          | asynchronous double gauge           | `<name>.max`        |
| `VALUE`        | asynchronous double gauge           | `<name>.value`      |
| `UNKNOWN`      | asynchronous double gauge           | `<name>.unknown`    |

The suffix is the statistic's Micrometer tag value, except for `TOTAL_TIME`, which uses `total_time`
so that it does not clash with `TOTAL`.

### Reading values back

Reading measurements back through the Micrometer API is not supported, because the bridge only
forwards them to OpenTelemetry. The first unsupported read logs a warning.

| Read                                                                                                                                                        | Result                                                                 |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `measure()` on any bridged meter                                                                                                                            | empty list                                                             |
| `Counter#count()`, `Gauge#value()`, `FunctionCounter#count()`, `FunctionTimer#count()`, `FunctionTimer#totalTime(TimeUnit)`, `FunctionTimer#mean(TimeUnit)` | `NaN`                                                                  |
| `Timer#count()`, `Timer#totalTime(TimeUnit)`, `DistributionSummary#count()`, `DistributionSummary#totalAmount()`                                            | real only when local state is kept, see below; otherwise `0` and `NaN` |
| `Timer#max(TimeUnit)`, `DistributionSummary#max()`, every `LongTaskTimer` read other than `measure()`                                                       | real                                                                   |

`Timer` and `DistributionSummary` keep their count and total locally only when the meter is
configured with percentiles, a percentile histogram, or service level objectives and the
[histogram gauges](#histograms-percentiles-and-service-level-objectives) are enabled.

Because of this, the OpenTelemetry registry is meant to be used alongside another registry rather
than as a replacement. When it is composed with a read-capable registry such as Spring Boot's
`SimpleMeterRegistry`, code that reads meters back can be served by that registry instead. The
javaagent's Spring Boot Actuator instrumentation and the Spring Boot starter both arrange this
automatically, keeping Spring Boot's fallback registry so that Actuator's metrics endpoint, which
reads from the first composite member holding the meter, is served by that registry rather than by
the OpenTelemetry one.

### Prometheus mode

Prometheus mode approximates the naming behavior of Micrometer's `PrometheusMeterRegistry`, for
users exporting with the Prometheus exporter. It forces the base time unit to seconds. For meters
whose Micrometer `Meter.Type` is `COUNTER` (including `FunctionCounter`), `DISTRIBUTION_SUMMARY`, or
`GAUGE`, it appends a non-empty base unit to the instrument name. For `TIMER` (including
`FunctionTimer`) and `LONG_TASK_TIMER`, it appends `seconds`; meters of type `OTHER` do not get a unit
suffix. For example, a `Timer` named `my.timer` becomes `my.timer.seconds`, and a
`DistributionSummary` named `my.summary` with the base unit `bytes` becomes `my.summary.bytes`.
Encoding the unit in the metric name is contrary to the OpenTelemetry naming rules, so this mode is
disabled by default and should only be enabled for Prometheus compatibility.

```java
MeterRegistry meterRegistry =
    OpenTelemetryMeterRegistry.builder(openTelemetry).setPrometheusMode(true).build();
```

For [custom meters](#custom-meters), the naming convention is applied to the combined name and
suffix instead of to the name alone, unless `otel.instrumentation.common.v3-preview` is enabled. A
custom `Meter` of type `COUNTER` named `my.meter` with the base unit `bytes` is therefore emitted as
`my.meter.count.bytes` rather than `my.meter.bytes.count`.
