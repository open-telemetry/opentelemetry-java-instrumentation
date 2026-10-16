# OSHI Instrumentation

## Current metrics

System metrics use schema 1.44.0 conventions.

Network packet counts use `system.network.packet.count`. Count units are `{packet}`,
`{error}`, and `{operation}` for packets, errors, and disk operations, respectively.
Memory metrics use `system.memory.state`; network I/O and errors use `network.interface.name`
and `network.io.direction`. Packet counts use `system.device`
and `network.io.direction`; disk metrics use `system.device` and `disk.io.direction`.

## Using OSHI with OpenTelemetry Java agent

Download the oshi-core jar from
<https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it
on the class path. OpenTelemetry Java agent uses the system class loader to
load classes from the oshi-core jar that are used for the metrics.
