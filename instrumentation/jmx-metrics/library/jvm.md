# JVM Metrics

Here is the list of metrics based on MBeans exposed by the JVM and that are defined in [`jvm.yaml`](./src/main/resources/jmx/rules/jvm.yaml).

Those metrics are defined in the [JVM runtime metrics semantic conventions](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/).

| Metric Name                                                                                                                           | semconv maturity | Type          | Attributes                            | Description                                         |
|---------------------------------------------------------------------------------------------------------------------------------------|:-----------------|---------------|---------------------------------------|-----------------------------------------------------|
| [jvm.memory.used](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmmemoryused)                              | stable           | UpDownCounter | jvm.memory.pool.name, jvm.memory.type | Used memory                                         |
| [jvm.memory.committed](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmmemorycommitted)                    | stable           | UpDownCounter | jvm.memory.pool.name, jvm.memory.type | Committed memory                                    |
| [jvm.memory.limit](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmmemorylimit)                            | stable           | UpDownCounter | jvm.memory.pool.name, jvm.memory.type | Max obtainable memory                               |
| [jvm.memory.init](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmmemoryinit)                              | experimental     | UpDownCounter | jvm.memory.pool.name, jvm.memory.type | Initial memory requested                            |
| [jvm.memory.used_after_last_gc](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmmemoryused_after_last_gc)  | stable           | UpDownCounter | jvm.memory.pool.name, jvm.memory.type | Memory used after latest GC                         |
| [jvm.thread.count](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmthreadcount)                            | stable           | UpDownCounter | [^1]                                  | Threads count                                       |
| [jvm.class.loaded](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmclassloaded)                            | stable           | Counter       |                                       | Classes loaded since JVM start                      |
| [jvm.class.unloaded](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmclassunloaded)                        | stable           | Counter       |                                       | Classes unloaded since JVM start                    |
| [jvm.class.count](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmclasscount)                              | stable           | UpDownCounter |                                       | Classes currently loaded count                      |
| [jvm.cpu.count](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmcpucount)                                  | stable           | UpDownCounter |                                       | Number of CPUs available                            |
| [jvm.cpu.time](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmcputime)                                    | stable           | Counter       |                                       | CPU time used by the process as reported by the JVM |
| [jvm.cpu.recent_utilization](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmcpurecent_utilization)        | stable           | Gauge         |                                       | Recent CPU utilization for process reported by JVM  |
| [jvm.file_descriptor.count](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmfile_descriptorcount)          | experimental     | UpDownCounter |                                       | Number of open file descriptors                     |
| [jvm.system.cpu.load_1m](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmsystemcpuload_1m)                 | experimental     | Gauge         |                                       | Average CPU load reported by JVM                    |
| [jvm.system.cpu.recent_utilization](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmcpurecent_utilization) | experimental     | Gauge         |                                       | Recent CPU utilization reported by JVM              |
| [jvm.buffer.memory.used](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmbuffermemoryused)                 | experimental     | UpDownCounter | jvm.buffer.pool.name                  | Memory used by buffers                              |
| [jvm.buffer.memory.limit](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmbuffermemorylimit)               | experimental     | UpDownCounter | jvm.buffer.pool.name                  | Maximum memory usage for buffers                    |
| [jvm.buffer.memory.count](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmbuffermemorycount)               | experimental     | UpDownCounter | jvm.buffer.pool.name                  | Buffers count                                       |

## Limitations and unsupported metrics

There are a few limitations to the JVM metrics that are captured through the JMX interface with declarative YAML.
Using the [runtime-telemetry](../../runtime-telemetry) modules with instrumentation allow to capture metrics without those limitations.

[^1]: `jvm.thread.daemon` and `jvm.thread.state` attributes are not supported.

- [jvm.gc.duration](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmgcduration) metric is not supported as it is only exposed through JMX notifications which are not supported with YAML.
