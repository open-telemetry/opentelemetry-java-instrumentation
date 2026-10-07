# OSHI Instrumentation

## Migrating to 3.0

System metrics now always use schema 1.44.0 conventions. Both the library and agent use
the instrumentation scope `io.opentelemetry.oshi-5.0`.

Update queries and dashboards from `system.network.packets` to `system.network.packet.count`.
Count units are now `{packet}`, `{error}`, and `{operation}`. Replace memory `state` with
`system.memory.state`; network I/O and errors use `network.interface.name` and
`network.io.direction` instead of `device` and `direction`. Packet counts use `system.device`
and `network.io.direction`; disk metrics use `system.device` and `disk.io.direction`.

The deprecated `ProcessMetrics` API and `otel.instrumentation.oshi.experimental-metrics.enabled`
setting have been removed.
Remove this setting from your configuration; it can no longer enable `runtime.java.memory`
or `runtime.java.cpu_time`.

Standard JVM metrics are available separately, but are not exact replacements:
`jvm.memory.used` measures JVM memory pools, not process RSS or virtual memory, and
`jvm.cpu.time` does not split user and system CPU time.

## Using OSHI with OpenTelemetry Java agent

Download the oshi-core jar from
<https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it
on the class path. OpenTelemetry Java agent uses the system class loader to
load classes from the oshi-core jar that are used for the metrics.
