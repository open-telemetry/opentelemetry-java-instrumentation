
The main entry point is the `JfrTelemetry` class in the package `io.opentelemetry.instrumentation.runtimetelemetryjfr`:

```java
// Initialize JfrTelemetry
JfrTelemetry jfrTelemetry = JfrTelemetry.create(openTelemetry);

// Close JfrTelemetry to stop listening for JFR events
jfrTelemetry.close();
```

`JfrTelemetry` works by subscribing to certain JFR events, and using relevant bits of information
from the events to produce telemetry data like metrics. The code is divided into "handlers", which
listen for specific events and produce relevant telemetry. The handlers are organized into
features (i.e `JfrFeature`), which represent a category of telemetry and can be toggled on and
off. `JfrTelemetry` evaluates which features are enabled, and only listens for the events required
by the handlers associated with those features.

Enable or disable a feature as follows:

```
JfrTelemetry jfrTelemetry = JfrTelemetry.builder(openTelemetry)
  .enableFeature(JfrFeature.BUFFER_METRICS)
  .disableFeature(JfrFeature.LOCK_METRICS)
  .build();
```

The following table describes the set of `JfrFeatures` available, whether each is enabled by
default, and the telemetry each produces:

<!-- DO NOT MANUALLY EDIT. Regenerate table following changes to instrumentation using ./gradlew generateDocs -->
<!-- generateDocsStart -->

| JfrFeature | Default Enabled | Metrics |
|---|---|---|
| BUFFER_METRICS | false | `process.runtime.jvm.buffer.count`, `process.runtime.jvm.buffer.limit`, `process.runtime.jvm.buffer.usage` |
| CLASS_LOAD_METRICS | false | `process.runtime.jvm.classes.current_loaded`, `process.runtime.jvm.classes.loaded`, `process.runtime.jvm.classes.unloaded` |
| CONTEXT_SWITCH_METRICS | true | `process.runtime.jvm.cpu.context_switch` |
| CPU_COUNT_METRICS | true | `process.runtime.jvm.cpu.limit` |
| CPU_UTILIZATION_METRICS | false | `process.runtime.jvm.cpu.utilization`, `process.runtime.jvm.system.cpu.utilization` |
| GC_DURATION_METRICS | false | `process.runtime.jvm.gc.duration` |
| LOCK_METRICS | true | `process.runtime.jvm.cpu.longlock` |
| MEMORY_ALLOCATION_METRICS | true | `process.runtime.jvm.memory.allocation` |
| MEMORY_POOL_METRICS | false | `process.runtime.jvm.memory.committed`, `process.runtime.jvm.memory.init`, `process.runtime.jvm.memory.limit`, `process.runtime.jvm.memory.usage`, `process.runtime.jvm.memory.usage_after_last_gc` |
| NETWORK_IO_METRICS | true | `process.runtime.jvm.network.io`, `process.runtime.jvm.network.time` |
| THREAD_METRICS | false | `process.runtime.jvm.threads.count` |
