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

Except for custom `Meter` instruments, the suffixes above are appended after the naming convention
has been applied. For a custom `Meter` the naming convention is applied to the suffixed name.

Instruments configured with percentiles or service level objectives can emit additional gauges; see
[Histograms](#histograms).

Micrometer tags are mapped to OpenTelemetry attributes, and the Micrometer meter description is
mapped to the OpenTelemetry instrument description. See [Names, tags, and
descriptions](#names-tags-and-descriptions) for details.

For a custom `Meter`, the OpenTelemetry instrument type is derived from each measurement's
`Statistic`: `COUNT`, `TOTAL`, and `TOTAL_TIME` become asynchronous double counters, `ACTIVE_TASKS`
becomes an asynchronous double up-down counter, and `DURATION`, `MAX`, `VALUE`, and `UNKNOWN` become
asynchronous double gauges. The name suffix is the statistic's Micrometer tag value representation,
so `ACTIVE_TASKS` produces `<name>.active`, not `<name>.active_tasks`. `TOTAL_TIME` is the one
exception: it uses `total_time` rather than its tag value representation `total`, to avoid clashing
with `TOTAL`.

### Units

The Micrometer base unit is used verbatim as the OpenTelemetry instrument unit, and instruments
registered without a base unit have an empty unit. Micrometer base units are not translated to
[UCUM](https://ucum.org/), which is what OpenTelemetry semantic conventions use, so a Micrometer
meter with the base unit `bytes` produces an OpenTelemetry instrument with the unit `bytes` rather
than `By`.

Timing instruments use the registry's base time unit, which is seconds by default and can be changed
with `OpenTelemetryMeterRegistryBuilder.setBaseTimeUnit(TimeUnit)`. It is reported as `ns`, `us`,
`ms`, `s`, `min`, `h`, or `d`.

### Histograms

Only Micrometer's service level objectives are mapped to the OpenTelemetry histogram's explicit
bucket boundaries advice, and for a `Timer` they are converted from nanoseconds to the base time
unit. `publishPercentileHistogram()`, `minimumExpectedValue`, and `maximumExpectedValue` do **not**
contribute bucket boundaries, so a `Timer` or `DistributionSummary` configured without service level
objectives produces a histogram with no boundaries advice, leaving bucket selection to the
OpenTelemetry SDK.

Micrometer percentiles cannot be represented as an OpenTelemetry histogram at all. Percentiles and
service level objectives can additionally be emitted as gauges by enabling
`io.opentelemetry.instrumentation.micrometer.v1_5.internal.Experimental#setMicrometerHistogramGaugesEnabled`,
which is experimental and may change at any time. These are Micrometer's own generated gauges, so
they are named `<name>.percentile` with a `phi` attribute and `<name>.histogram` with an `le`
attribute. Percentile gauges inherit the source meter's base unit, which for a `Timer` is empty even
though the values are expressed in the base time unit, and bucket count gauges have no unit.

`LongTaskTimer` is the exception to the setting above: it is not bridged to an OpenTelemetry
histogram, and it always emits the `<name>.percentile` and `<name>.histogram` gauges when
percentiles or service level objectives are configured on it, regardless of whether the experimental
setting is enabled.

The decaying `<name>.max` gauge emitted alongside the `Timer` and `DistributionSummary` histograms
is deprecated and will be removed in 3.0. OpenTelemetry histograms already carry a max, and the
`<name>` / `<name>.max` pair violates the OpenTelemetry metric naming rules. It is no longer emitted
when `otel.instrumentation.common.v3-preview` is enabled.

### Names, tags, and descriptions

Metrics are emitted under the instrumentation scope `io.opentelemetry.micrometer-1.5`.

Tag keys and values are passed through the registry's naming convention before becoming attribute
keys and values.

Meter descriptions are deduplicated by the emitted OpenTelemetry instrument name: the first
description registered for a given name is reused for every instrument that later shares that name,
and a `null` description is mapped to an empty description.

This reconciles a difference between the two data models. In Micrometer, each set of tags is a
separate meter and may carry its own description, whereas in OpenTelemetry the description is one of
an instrument's identifying fields. Bridging the descriptions through unchanged would therefore turn
a single Micrometer metric into several conflicting OpenTelemetry instruments that share a name, so
the SDK would emit duplicate registration warnings and export the tag sets as separate metric
streams instead of aggregating them into one. Micrometer's own `PrometheusMeterRegistry` resolves
this the same way, by keeping one description per metric.

The deduplication is scoped to the OpenTelemetry `MeterProvider` rather than to the Micrometer
registry, so the description that wins depends on registration order, and registries sharing a
`MeterProvider` share descriptions. Because the key is the name after the naming convention has been
applied, meters that share a Micrometer name but map onto different instrument names — for example a
`Counter` and a `Timer` named `foo` in [prometheus mode](#prometheus-mode), which become `foo` and
`foo.seconds` — each keep their own description.

### Prometheus mode

Prometheus mode approximates the naming behavior of Micrometer's `PrometheusMeterRegistry` and is
intended for users exporting with the Prometheus exporter. It forces the base time unit to seconds
and appends the unit to the instrument name. The naming convention keys off the Micrometer meter
type, so `Timer`, `FunctionTimer`, and `LongTaskTimer` names get `.seconds`, while `Counter`,
`FunctionCounter`, `DistributionSummary`, and `Gauge` names get their base unit appended when they
have a non-empty one. Only custom `Meter` instruments are left unchanged.

Any suffix from the table above is appended afterwards, so a `Timer` named `my.timer` produces the
OpenTelemetry instrument `my.timer.seconds`, a `DistributionSummary` named `my.summary` with the
base unit `bytes` produces `my.summary.bytes`, and a `FunctionTimer` named `my.function` produces
`my.function.seconds.count` and `my.function.seconds.sum`.
These are the OpenTelemetry instrument names; replacing `.` with `_` and appending `_total` to
counters is left to the Prometheus exporter, so the final Prometheus series names differ. Encoding
the unit in the metric name is contrary to the OpenTelemetry naming rules, so this mode is disabled
by default and should only be enabled for Prometheus compatibility.

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
