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
applied. The default naming convention passes the name through unchanged; see
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

The suffixes above are appended after the naming convention has been applied, except for a custom
`Meter`, where the convention is applied to the already-suffixed name.

Tags become attributes and the meter description becomes the instrument description; see [Names,
tags, and descriptions](#names-tags-and-descriptions). Instruments configured with percentiles or
service level objectives can emit additional gauges; see [Histograms](#histograms).

For a custom `Meter`, each measurement's `Statistic` determines the instrument type: `COUNT`,
`TOTAL`, and `TOTAL_TIME` become asynchronous double counters, `ACTIVE_TASKS` becomes an
asynchronous double up-down counter, and `DURATION`, `MAX`, `VALUE`, and `UNKNOWN` become
asynchronous double gauges. The suffix is the statistic's Micrometer tag value, so `ACTIVE_TASKS`
produces `<name>.active`, not `<name>.active_tasks`. The exception is `TOTAL_TIME`, which uses
`total_time` rather than its tag value `total`, to avoid clashing with `TOTAL`.

### Units

The Micrometer base unit is used verbatim as the OpenTelemetry instrument unit, and meters
registered without one have an empty unit. Base units are not translated to
[UCUM](https://ucum.org/), which OpenTelemetry semantic conventions use, so the base unit `bytes`
produces the unit `bytes` rather than `By`.

Timing instruments use the registry's base time unit, which is seconds by default and can be changed
with `OpenTelemetryMeterRegistryBuilder.setBaseTimeUnit(TimeUnit)`. It is reported as `ns`, `us`,
`ms`, `s`, `min`, `h`, or `d`.

### Histograms

Only Micrometer's service level objectives become the OpenTelemetry histogram's explicit bucket
boundaries advice, converted from nanoseconds to the base time unit for a `Timer`.
`publishPercentileHistogram()`, `minimumExpectedValue`, and `maximumExpectedValue` do **not**
contribute boundaries, so a `Timer` or `DistributionSummary` without service level objectives
produces no boundaries advice, leaving bucket selection to the OpenTelemetry SDK.

Micrometer percentiles cannot be represented as an OpenTelemetry histogram at all. Enabling
`io.opentelemetry.instrumentation.micrometer.v1_5.internal.Experimental#setMicrometerHistogramGaugesEnabled`,
which is experimental and may change at any time, additionally emits Micrometer's own gauges:
`<name>.percentile` with a `phi` attribute and `<name>.histogram` with an `le` attribute. Percentile
gauges inherit the source meter's base unit, which for a `Timer` is empty even though the values are
in the base time unit; bucket count gauges have no unit.

`LongTaskTimer` is the exception: it is not bridged to an OpenTelemetry histogram, so it always
emits those gauges when percentiles or service level objectives are configured on it, regardless of
the experimental setting.

The decaying `<name>.max` gauge emitted alongside the `Timer` and `DistributionSummary` histograms
is deprecated and will be removed in 3.0: OpenTelemetry histograms already carry a max, and the
`<name>` / `<name>.max` pair violates the OpenTelemetry metric naming rules. It is no longer emitted
when `otel.instrumentation.common.v3-preview` is enabled.

### Names, tags, and descriptions

Metrics are emitted under the instrumentation scope `io.opentelemetry.micrometer-1.5`.

Tag keys and values are passed through the registry's naming convention before becoming attribute
keys and values.

Meter descriptions are deduplicated by the emitted OpenTelemetry instrument name: the first
description registered for a name is reused by every instrument that later shares it, and a `null`
description becomes an empty description.

This reconciles a data model difference: in Micrometer each set of tags is a separate meter with its
own description, while in OpenTelemetry the description is one of an instrument's identifying
fields. Passing descriptions through unchanged would turn one Micrometer metric into several
conflicting instruments sharing a name, which the SDK exports as separate metric streams instead of
aggregating them, along with duplicate registration warnings. Micrometer's own
`PrometheusMeterRegistry` resolves this the same way, by keeping one description per metric.

Deduplication is scoped to the OpenTelemetry `MeterProvider` rather than to the Micrometer registry,
so the winning description depends on registration order, and registries sharing a `MeterProvider`
share descriptions. Because the key is the emitted name, meters that share a Micrometer name but map
onto different instrument names each keep their own description — for example a `Counter` and a
`Timer` named `foo` in [prometheus mode](#prometheus-mode), which become `foo` and `foo.seconds`.

### Prometheus mode

Prometheus mode approximates the naming behavior of Micrometer's `PrometheusMeterRegistry`, for
users exporting with the Prometheus exporter. It forces the base time unit to seconds and appends
the unit to the instrument name, keying off the Micrometer meter type: `Timer`, `FunctionTimer`, and
`LongTaskTimer` get `.seconds`, while `Counter`, `FunctionCounter`, `DistributionSummary`, and
`Gauge` get their base unit when they have a non-empty one. Only custom `Meter` instruments are
unchanged.

Any suffix from the table above is appended afterwards, so `my.timer` (a `Timer`) becomes
`my.timer.seconds`, `my.summary` (a `DistributionSummary` with the base unit `bytes`) becomes
`my.summary.bytes`, and `my.function` (a `FunctionTimer`) becomes `my.function.seconds.count` and
`my.function.seconds.sum`. These are the OpenTelemetry instrument names; the Prometheus exporter
still replaces `.` with `_` and appends `_total` to counters, so the final series names differ.
Encoding the unit in the metric name is contrary to the OpenTelemetry naming rules, so this mode is
disabled by default and should only be enabled for Prometheus compatibility.

```java
MeterRegistry meterRegistry =
    OpenTelemetryMeterRegistry.builder(openTelemetry).setPrometheusMode(true).build();
```

### Reading measurements is mostly not supported

The bridge forwards measurements to OpenTelemetry rather than keeping them readable through the
Micrometer API, and it logs a warning the first time an unsupported read is attempted.

`Meter#measure()` returns an empty list for every bridged instrument. `Counter#count()`,
`FunctionCounter#count()`, `Gauge#value()`, `FunctionTimer#count()`, `FunctionTimer#totalTime()`,
and `FunctionTimer#mean()` return `Double.NaN`.
`Timer` and `DistributionSummary` only track `count()` and `totalTime()` / `totalAmount()` locally
when the meter is configured with percentiles or service level objectives *and* gauge-based
Micrometer histograms are enabled; otherwise `count()` returns `0` and the totals return
`Double.NaN`. Their `max()` is always tracked, but it is a decaying max over the configured
distribution statistic expiry window rather than the all-time max. `LongTaskTimer` is the one
instrument whose reads mostly work, because it extends Micrometer's `DefaultLongTaskTimer` and only
overrides `measure()`.

Code that reads values back from the registry, such as a Micrometer-based health check or test
assertion, should read from the OpenTelemetry SDK instead.
