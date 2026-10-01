# Settings for the Micrometer bridge instrumentation

| System property                                                         | Type    | Default | Description                                                                                                                                                                                                                                                 |
| ----------------------------------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `otel.instrumentation.micrometer.base-time-unit`                        | String  | `s`     | Set the base time unit for the OpenTelemetry `MeterRegistry` implementation. <details><summary>Valid values</summary>`ns`, `nanoseconds`, `us`, `microseconds`, `ms`, `milliseconds`, `s`, `seconds`, `min`, `minutes`, `h`, `hours`, `d`, `days`</details> |
| `otel.instrumentation.micrometer.prometheus-mode.enabled`               | Boolean | `false` | Enable the "Prometheus mode" this will simulate the behavior of Micrometer's PrometheusMeterRegistry. The instruments will be renamed to match Micrometer instrument naming, and the base time unit will be set to seconds.                                 |
| `otel.instrumentation.micrometer.experimental.histogram-gauges.enabled` | Boolean | `false` | Enables the generation of gauge-based Micrometer histograms for `DistributionSummary` and `Timer` instruments.                                                                                                                                              |
| `otel.instrumentation.micrometer.histogram-gauges.enabled`              | Boolean | `false` | Deprecated alias for `otel.instrumentation.micrometer.experimental.histogram-gauges.enabled`. It will be removed in 3.0.                                                                                                                                    |


## Prefer agent JVM metrics

Enable the bridge and opt into the agent's representation of reviewed standard JVM observations:

```properties
otel.instrumentation.micrometer.enabled=true
otel.instrumentation.micrometer.experimental.jvm-metrics-ownership.enabled=true
```

Ownership is disabled by default. Each exact name and type below is suppressed only if its
corresponding JMX observer (or GC notification listener) registered before the bridge initialized.
Disabled, unsupported, JFR-only, or late replacements retain the Micrometer copy. Experimental
buffer, system CPU, and file-descriptor replacements additionally require
`otel.instrumentation.runtime-telemetry.emit-experimental-metrics=true`.

| Micrometer name | Agent replacement | Micrometer type |
| --- | --- | --- |
| `jvm.classes.loaded`, `jvm.class.count` | `jvm.class.count` | Gauge |
| `jvm.classes.loaded.count`, `jvm.class.loaded` | `jvm.class.loaded` | Counter/function counter |
| `jvm.classes.unloaded`, `jvm.class.unloaded` | `jvm.class.unloaded` | Counter/function counter |
| `jvm.memory.used`, `jvm.memory.committed` | Same name | Gauge |
| `jvm.memory.max`, `jvm.memory.limit` | `jvm.memory.limit` | Gauge |
| `jvm.buffer.count`, `jvm.buffer.memory.used` | Same name | Gauge |
| `jvm.buffer.total.capacity` | `jvm.buffer.memory.limit` | Gauge |
| `jvm.threads.live`, `jvm.threads.daemon`, `jvm.threads.states`, `jvm.thread.count` | `jvm.thread.count` | Gauge |
| `system.cpu.count`, `jvm.cpu.count` | `jvm.cpu.count` | Gauge |
| `process.cpu.usage`, `jvm.cpu.recent_utilization` | `jvm.cpu.recent_utilization` | Gauge |
| `process.cpu.time`, `jvm.cpu.time` | `jvm.cpu.time` | Counter/function counter |
| `system.cpu.usage` | `jvm.system.cpu.utilization` | Gauge |
| `system.load.average.1m` | `jvm.system.cpu.load_1m` | Gauge |
| `process.files.open`, `process.files.max` | `jvm.file_descriptor.count`, `jvm.file_descriptor.limit`, respectively | Gauge |
| `jvm.gc.pause`, `jvm.gc.concurrent.phase.time` | `jvm.gc.duration` | Timer |

This selects native units and attributes, rather than preserving identical series. Native thread
counts partition platform threads by daemon flag and state; summing those partitions replaces live
and per-state counts. Native GC duration includes both pause and concurrent-phase notifications;
GC cause capture is separately configurable. Memory/buffer values of `-1` (unavailable) are omitted
by runtime telemetry. CPU time uses seconds in the native representation.

Keep an exact registered Micrometer name, including all its generated companion instruments:

```properties
otel.instrumentation.micrometer.experimental.jvm-metrics-ownership.kept=jvm.memory.used,jvm.gc.pause
```

Keep exceptions permit both copies. Matching uses the name after meter filters and before export
naming conventions. Custom meters reusing an eligible name/type can also be suppressed: keep them
explicitly or leave ownership disabled. The setting affects only the agent's bridge; other
Micrometer registries continue recording. Observer registration does not guarantee export through
SDK views, and startup decisions do not change when runtime telemetry later starts or stops.

All unlisted names remain bridged, including these complementary observations:

- `jvm.memory.usage.after.gc`: a long-lived heap utilization ratio, not the agent's post-GC byte count.
- `jvm.gc.memory.allocated`, `jvm.gc.memory.promoted`, `jvm.gc.live.data.size`,
  `jvm.gc.max.data.size`, `jvm.gc.overhead`, `jvm.gc.cpu.time`.
- `jvm.threads.peak`, `jvm.threads.started`, `jvm.threads.deadlocked`,
  `jvm.threads.deadlocked.monitor`, `jvm.threads.virtual.pinned`,
  `jvm.threads.virtual.submit.failed`.
- `jvm.compilation.time`, `jvm.info`, `process.uptime`, `process.start.time`.

The policy does not use `jvm.*`, `process.*`, or `system.*` wildcard suppression. This experimental
coverage expansion adds the reviewed families above to the earlier class-loading prototype.
