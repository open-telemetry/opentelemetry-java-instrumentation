# Settings for the Micrometer bridge instrumentation

| System property                                                         | Type    | Default | Description                                                                                                                                                                                                                                                 |
| ----------------------------------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.micrometer.base-time-unit`                        | String  | `s`     | Set the base time unit for the OpenTelemetry `MeterRegistry` implementation. <details><summary>Valid values</summary>`ns`, `nanoseconds`, `us`, `microseconds`, `ms`, `milliseconds`, `s`, `seconds`, `min`, `minutes`, `h`, `hours`, `d`, `days`</details> |
| `otel.instrumentation.micrometer.prometheus-mode.enabled`               | Boolean | `false` | Enable the "Prometheus mode" this will simulate the behavior of Micrometer's PrometheusMeterRegistry. The instruments will be renamed to match Micrometer instrument naming, and the base time unit will be set to seconds.                                 |
| `otel.instrumentation.micrometer.experimental.histogram-gauges.enabled` | Boolean | `false` | Enables the generation of gauge-based Micrometer histograms for `DistributionSummary` and `Timer` instruments.                                                                                                                                              |

## Prefer agent JVM metrics

Enable the bridge and opt into the agent's representation of supported standard JVM observations:

```properties
otel.instrumentation.micrometer.enabled=true
otel.instrumentation.micrometer.experimental.jvm-metrics-ownership.enabled=true
```

Ownership is disabled by default. When enabled, the bridge stops bridging each exact name and type
below. If `otel.instrumentation.runtime-telemetry.enabled=false`, nothing is suppressed, so the
Micrometer metrics remain bridged. If you set `otel.instrumentation.common.default-enabled=false`,
also set `otel.instrumentation.runtime-telemetry.enabled=true`; otherwise the listed metrics are
suppressed without a runtime telemetry replacement. Rows marked experimental are suppressed only
when `otel.instrumentation.runtime-telemetry.emit-experimental-metrics=true`, since runtime
telemetry emits those replacements only in that mode.

| Micrometer name | Agent replacement | Micrometer type |
| --- | --- | --- |
| `jvm.classes.loaded`, `jvm.class.count` | `jvm.class.count` | Gauge |
| `jvm.classes.loaded.count`, `jvm.class.loaded` | `jvm.class.loaded` | Counter/function counter |
| `jvm.classes.unloaded`, `jvm.class.unloaded` | `jvm.class.unloaded` | Counter/function counter |
| `jvm.memory.used`, `jvm.memory.committed` | Same name | Gauge |
| `jvm.memory.max`, `jvm.memory.limit` | `jvm.memory.limit` | Gauge |
| `jvm.buffer.count`, `jvm.buffer.memory.used` (experimental) | Same name | Gauge |
| `jvm.buffer.total.capacity` (experimental) | `jvm.buffer.memory.limit` | Gauge |
| `jvm.threads.live`, `jvm.threads.daemon`, `jvm.threads.states`, `jvm.thread.count` | `jvm.thread.count` | Gauge |
| `system.cpu.count`, `jvm.cpu.count` | `jvm.cpu.count` | Gauge |
| `process.cpu.usage`, `jvm.cpu.recent_utilization` | `jvm.cpu.recent_utilization` | Gauge |
| `process.cpu.time`, `jvm.cpu.time` | `jvm.cpu.time` | Counter/function counter |
| `system.cpu.usage` (experimental) | `jvm.system.cpu.utilization` | Gauge |
| `system.load.average.1m` (experimental) | `jvm.system.cpu.load_1m` | Gauge |
| `process.files.open`, `process.files.max` (experimental) | `jvm.file_descriptor.count`, `jvm.file_descriptor.limit`, respectively | Gauge |
| `jvm.gc.pause`, `jvm.gc.concurrent.phase.time` | `jvm.gc.duration` | Timer |

This selects native units and attributes, rather than preserving identical series. Native thread
counts partition platform threads by daemon flag and state; summing those partitions replaces live
and per-state counts. Native GC duration includes both pause and concurrent-phase notifications
and always includes `jvm.gc.cause`. Memory/buffer values of `-1` (unavailable) are omitted by
runtime telemetry. CPU time uses seconds in the native representation.

Matching uses the name after meter filters and before export naming conventions. Custom meters
reusing a listed name and type are also suppressed; leave ownership disabled to keep them. The
setting affects only the agent's bridge; other Micrometer registries continue recording.

Metrics not listed in the table remain bridged. Matching uses exact names and types; wildcard
suppression is not supported.
