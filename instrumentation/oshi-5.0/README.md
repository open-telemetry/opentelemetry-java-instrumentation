# OSHI Instrumentation

## Settings for the OSHI instrumentation

| System property                                           | Type    | Default | Description              |
|-----------------------------------------------------------| ------- | ------- |--------------------------|
| `otel.instrumentation.oshi.experimental-metrics.enabled`  | Boolean | `false` | Enable the OSHI metrics. |

## Using OSHI with OpenTelemetry Java agent

Download oshi-core jar from <https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it on the class path. OpenTelemetry Java agent uses system class loader to load classes from the oshi-core jar that are used for the metrics.
