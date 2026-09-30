# OSHI Instrumentation

## Settings for the OSHI instrumentation

| System property                                          | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                                                          |
| -------------------------------------------------------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `otel.instrumentation.oshi.experimental-metrics.enabled` | Boolean | `false` | Deprecated. Outside v3 preview, enable the `runtime.java.memory` and `runtime.java.cpu_time` process metrics. This setting is ignored when `otel.instrumentation.common.v3-preview=true`. Will be removed in 3.0. Use the standard JVM metrics `jvm.memory.used` and `jvm.cpu.time` instead. These are not exact replacements: `jvm.memory.used` measures JVM memory pools rather than process RSS or virtual memory, and `jvm.cpu.time` does not separate user and system CPU time. |

## Using OSHI with OpenTelemetry Java agent

Download the oshi-core jar from
<https://central.sonatype.com/artifact/com.github.oshi/oshi-core> and place it
on the class path. OpenTelemetry Java agent uses the system class loader to
load classes from the oshi-core jar that are used for the metrics.
