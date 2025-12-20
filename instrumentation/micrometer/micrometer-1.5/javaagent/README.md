# Settings for the Micrometer bridge instrumentation

| System property                                            | Type    | Default | Description                                                                                                                                                                                                                                                 |
| ---------------------------------------------------------- |---------| ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.micrometer.base-time-unit`           | String  | `s`     | Set the base time unit for the OpenTelemetry `MeterRegistry` implementation. <details><summary>Valid values</summary>`ns`, `nanoseconds`, `us`, `microseconds`, `ms`, `milliseconds`, `s`, `seconds`, `min`, `minutes`, `h`, `hours`, `d`, `days`</details> |
| `otel.instrumentation.micrometer.prometheus-mode.enabled`  | Boolean | `false` | Enable the "Prometheus mode" this will simulate the behavior of Micrometer's PrometheusMeterRegistry. The instruments will be renamed to match Micrometer instrument naming, and the base time unit will be set to seconds.                                 |
| `otel.instrumentation.micrometer.histogram-gauges.enabled` | Boolean | `false` | Enables the generation of gauge-based Micrometer histograms for `DistributionSummary` and `Timer` instruments.                                                                                                                                              |

## Configuring Metric Aggregation Temporality

If you need to configure whether metrics are exported with **DELTA** or **CUMULATIVE** temporality, this should be configured at the **SDK/exporter level**, not at the instrumentation level.

### For OTLP Exporter

Use the standard OpenTelemetry SDK configuration:

- **Environment variable**: `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE`
- **System property**: `-Dotel.exporter.otlp.metrics.temporality.preference`

Valid values:
- `CUMULATIVE` (default) - Metrics represent the accumulated value since the start of the application
- `DELTA` - Metrics represent the change since the last export
- `LOW_MEMORY` - Uses DELTA for synchronous instruments and CUMULATIVE for asynchronous instruments

Example:
```bash
# Using environment variable
export OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=DELTA

# Using system property
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.exporter.otlp.metrics.temporality.preference=DELTA \
     -jar myapp.jar
```

### Why Not Configure at Instrumentation Level?

Aggregation temporality is a **data export concern**, not a data collection concern:
- The Micrometer bridge creates metrics using the OpenTelemetry API
- The temporality is determined by the configured metric exporter (OTLP, Prometheus, etc.)
- Configuring temporality at the SDK level affects **all** metrics consistently, not just Micrometer metrics
- This aligns with the OpenTelemetry architecture where instrumentation handles data collection and the SDK handles data export

For more information, see the [OpenTelemetry specification on metric temporality](https://opentelemetry.io/docs/specs/otel/metrics/data-model/#temporality).
