# OSHI Instrumentation

## Settings for the OSHI instrumentation

| System property                                          | Type    | Default | Description              |
| -------------------------------------------------------- | ------- | ------- | ------------------------ |
| `otel.instrumentation.oshi.experimental-metrics.enabled` | Boolean | `false` | Enable the OSHI metrics. |

By default, OSHI system metrics use the legacy 1.19.0 conventions and schema
with the `io.opentelemetry.oshi` scope. Setting
`otel.instrumentation.common.v3-preview=true` switches system metrics to the
1.44.0 conventions and schema and the `io.opentelemetry.oshi-5.0` scope. For
example, `system.network.packets` (`{packets}`) becomes
`system.network.packet.count` (`{packet}`), and `device` and `direction`
become domain-specific attributes. The optional `runtime.java.*` process
metrics remain schema-less under either setting.

## Using OSHI with OpenTelemetry Java agent

Download the oshi-core jar from
<https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it
on the class path. OpenTelemetry Java agent uses the system class loader to
load classes from the oshi-core jar that are used for the metrics.
