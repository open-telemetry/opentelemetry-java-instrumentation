# Runtime Telemetry Java 17

The main entry point is the `RuntimeMetrics` class in the package `io.opentelemetry.instrumentation.runtimemetrics.java17`:

```java
// Initialize JfrTelemetry
RuntimeMetrics runtimeMetrics = RuntimeMetrics.create(openTelemetry);

// Close JfrTelemetry to stop listening for JFR events
runtimeMetrics.close();
```

`RuntimeMetrics` uses two underlying implementations to gather the full set of metric data, JFR and JMX.
The metrics gathered by the two implementations are mutually exclusive and the union of them produces
the full set of available metrics.
The JMX component is reused from the `io.opentelemetry.instrumentation.runtimemetrics.java8` package.
The JFR component uses JFR streaming and is only available in JAVA 17.
It works by subscribing to certain JFR events, and using relevant bits of information
from the events to produce telemetry data like metrics. The code is divided into "handlers", which
listen for specific events and produce relevant telemetry. The handlers are organized into
features (i.e `JfrFeature`), which represent a category of telemetry and can be toggled on and
off. `RuntimeMetrics` evaluates which features are enabled, and only listens for the events required
by the handlers associated with those features.

Enable or disable a feature as follows:

```
RuntimeMetrics runtimeMetrics = RuntimeMetrics.builder(openTelemetry)
  .enableFeature(JfrFeature.BUFFER_METRICS)
  .disableFeature(JfrFeature.LOCK_METRICS)
  .build();
```

The following table describes the set of `JfrFeatures` available, whether each is enabled by
default, and the telemetry each produces:

<!-- DO NOT MANUALLY EDIT. Regenerate table following changes to instrumentation using ./gradlew generateDocs -->
<!-- generateDocsStart -->

**Warning**: JFR events might not be available for all JVMs or with a GraalVM native image, therefore limiting the produced metrics. The original implementation was done for Hotspot. OpenJ9 currently (Nov. 2025) only has the VM-level JFR implementation. So events emitted at the Java level (ie. in jdk.jfr) will not be present. Meaning, jdk.SocketRead, jdk.SocketWrite won't work.

| JfrFeature                | Jfr Event names                                                                              | Default Enabled | Metric names                                                                                                      |
|---------------------------|----------------------------------------------------------------------------------------------|-----------------|-------------------------------------------------------------------------------------------------------------------|
| BUFFER_METRICS            | `jdk.DirectBufferStatistics`[6]                                                              | `false`         | `jvm.buffer.count`, `jvm.buffer.memory.limit`, `jvm.buffer.memory.used`                                           |
| CLASS_LOAD_METRICS        | `jdk.ClassLoadingStatistics`[5]                                                              | `false`         | `jvm.class.count`, `jvm.class.loaded`, `jvm.class.unloaded`                                                       |
| CONTEXT_SWITCH_METRICS    | `jdk.ThreadContextSwitchRate`[6]                                                             | `true`          | `jvm.cpu.context_switch`                                                                                          |
| CPU_COUNT_METRICS         | `jdk.ContainerConfiguration`                                                                 | `true`          | `jvm.cpu.limit`                                                                                                   |
| CPU_UTILIZATION_METRICS   | `jdk.CPULoad`[6]                                                                             | `false`         | `jvm.cpu.recent_utilization`, `jvm.system.cpu.utilization`                                                        |
| GC_DURATION_METRICS       | `jdk.G1GarbageCollection`[1], `jdk.OldGarbageCollection`[4], `jdk.YoungGarbageCollection`[4] | `false`         | `jvm.gc.duration`                                                                                                 |
| LOCK_METRICS              | `jdk.JavaMonitorWait`                                                                        | `true`          | `jvm.cpu.longlock`                                                                                                |
| MEMORY_ALLOCATION_METRICS | `jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB`[2]                        | `true`          | `jvm.memory.allocation`                                                                                           |
| MEMORY_POOL_METRICS       | `jdk.G1HeapSummary`[1], `jdk.MetaspaceSummary`[2], `jdk.PSHeapSummary`[3]                    | `false`         | `jvm.memory.committed`, `jvm.memory.init`, `jvm.memory.limit`, `jvm.memory.used`, `jvm.memory.used_after_last_gc` |
| NETWORK_IO_METRICS        | `jdk.SocketRead`, `jdk.SocketWrite`                                                          | `true`          | `jvm.network.io`, `jvm.network.time`                                                                              |
| THREAD_METRICS            | `jdk.JavaThreadStatistics`                                                                   | `false`         | `jvm.thread.count`                                                                                                |

**[1]** - G1 doesn't exist if you use the [community edition](https://www.graalvm.org/community/) for GraalVM native image.

**[2]** - Not applicable for GraalVM native image.

**[3]** - No parallel GC on GraalVM native image.

**[4]** - On GraalVM there is no true "old" and "eden" spaces. Everything is allocated in linked heap chunks.

**[5]** - No dynamic class loading on GraalVM.

**[6]** - Possible but not implemented on GraalVM, as of Nov. 2025.
