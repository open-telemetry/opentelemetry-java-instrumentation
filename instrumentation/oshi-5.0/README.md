# OSHI Instrumentation

## Settings for the OSHI instrumentation

| System property                                          | Type    | Default | Description                                                                                                                                                                |
| -------------------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.oshi.experimental-metrics.enabled` | Boolean | `false` | Deprecated. Enable the `runtime.java.memory` and `runtime.java.cpu_time` process metrics outside v3 preview. Ignored under v3 preview and will be removed in 3.0. |

## Using OSHI with OpenTelemetry Java agent

Download the oshi-core jar from
<https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it
on the class path. OpenTelemetry Java agent uses the system class loader to
load classes from the oshi-core jar that are used for the metrics.

The Java agent continues to emit all OSHI system metrics under v3 preview. Enable preview with
`otel.instrumentation.common.v3-preview=true`; in this mode, the deprecated process metric setting
is not read and the two `runtime.java.*` process metrics are not registered. The equivalent
declarative YAML path for the setting is
`java.oshi.experimental_metrics/development.enabled`.

There is no automatic Java agent replacement for the retired process metrics. A separately
configured OpenTelemetry Collector
[hostmetrics process scraper](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/hostmetricsreceiver/internal/scraper/processscraper/documentation.md)
can provide `process.memory.usage`, `process.memory.virtual`, and `process.cpu.time`. These metrics
are not drop-in replacements: CPU time is a cumulative counter in seconds instead of a millisecond
gauge, and metric names, breakdown attributes, resource and process selection, and backend queries
must be migrated.
