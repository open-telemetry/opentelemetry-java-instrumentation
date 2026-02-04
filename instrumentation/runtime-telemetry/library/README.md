# JVM Runtime Metrics

This module provides JVM runtime metrics as documented in the [semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md).

This is the unified runtime telemetry module that works on all Java versions. On Java 8-16, it uses
JMX for metrics collection. On Java 17+, it can additionally use JFR (Java Flight Recorder) for
metrics that are not available via JMX.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-runtime-telemetry).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-runtime-telemetry</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-runtime-telemetry:OPENTELEMETRY_VERSION")
```

### Usage

Register JVM runtime metrics:

```java
OpenTelemetry openTelemetry = // OpenTelemetry instance configured elsewhere

RuntimeTelemetry runtimeTelemetry = RuntimeTelemetry.create(openTelemetry);

// When done, close to stop metric collection
runtimeTelemetry.close();
```

To select specific metrics, configure [metric views](https://opentelemetry.io/docs/languages/java/sdk/#views)
on the SDK to filter or customize which metrics are exported.

For example, using [declarative configuration](https://github.com/open-telemetry/opentelemetry-java-examples/tree/main/declarative-configuration):

```yaml
meter_provider:
  views:
    # Drop jvm.memory.committed metric
    - selector:
        instrument_name: jvm.memory.committed
      stream:
        aggregation:
          drop:
    # Only retain jvm.memory.type attribute on jvm.memory.used
    - selector:
        instrument_name: jvm.memory.used
      stream:
        attribute_keys:
          included:
            - jvm.memory.type
```

To retain only `jvm.memory.used` and drop all other JVM runtime metrics:

```yaml
meter_provider:
  views:
    # Drop all metrics from this instrumentation scope
    - selector:
        meter_name: io.opentelemetry.runtime-telemetry
      stream:
        aggregation:
          drop:
    # Keep jvm.memory.used (views are additive, this creates a second stream)
    - selector:
        meter_name: io.opentelemetry.runtime-telemetry
        instrument_name: jvm.memory.used
      stream: {}
```

## Metrics

### Stable Metrics (enabled by default)

These metrics are collected via JMX on all Java versions:

| Metric | Description |
| -------- | ----------- |
| `jvm.class.count` | Number of classes currently loaded |
| `jvm.class.loaded` | Number of classes loaded since JVM start |
| `jvm.class.unloaded` | Number of classes unloaded since JVM start |
| `jvm.cpu.recent_utilization` | Recent CPU utilization for the process |
| `jvm.cpu.time` | CPU time used by the process |
| `jvm.gc.duration` | Duration of JVM garbage collection actions |
| `jvm.memory.committed` | Measure of memory committed |
| `jvm.memory.limit` | Measure of max obtainable memory |
| `jvm.memory.used` | Measure of memory used |
| `jvm.memory.used_after_last_gc` | Measure of memory used, as measured after the most recent garbage collection event on this pool |
| `jvm.thread.count` | Number of executing platform threads |

### Experimental Metrics

These metrics are enabled with `emitExperimentalMetrics()`:

**JMX-based (all Java versions):**

| Metric | Description |
| -------- | ----------- |
| `jvm.buffer.count` | Number of buffers in the pool |
| `jvm.buffer.memory.limit` | Measure of total memory capacity of buffers |
| `jvm.buffer.memory.used` | Measure of memory used by buffers |
| `jvm.memory.init` | Measure of initial memory requested |
| `jvm.system.cpu.utilization` | System-wide CPU utilization |

**JFR-based (Java 17+ only):**

| Metric | Description |
| -------- | ----------- |
| `jvm.cpu.context_switch` | Context switch rate |
| `jvm.cpu.longlock` | Long lock contention |
| `jvm.memory.allocation` | Memory allocation rate |
| `jvm.network.io` | Network I/O bytes |
| `jvm.network.time` | Network I/O time |

## Garbage Collector Dependent Metrics

The attributes reported on the memory metrics (`jvm.memory.*`) and gc metrics (`jvm.gc.*`) are dependent on the garbage collector used by the application, since each garbage collector organizes memory pools differently and has different strategies for reclaiming memory during garbage collection.

The following lists attributes reported for a variety of garbage collectors. Notice that attributes are not necessarily constant across `*.used`, `*.committed`, and `*.limit` since not all memory pools report a limit.

- CMS Garbage Collector
  - `jvm.memory.used`: {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Par Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Par Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Code Cache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.committed`: {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Par Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Par Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Code Cache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.limit`: {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Par Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Par Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Code Cache,jvm.memory.type=non_heap}
  - `jvm.memory.used_after_last_gc`: {jvm.memory.pool.name=Par Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Par Survivor Space,jvm.memory.type=heap}
  - `jvm.gc.duration`: {jvm.gc.action=end of minor GC,jvm.gc.name=ParNew}, {jvm.gc.action=end of major GC,jvm.gc.name=MarkSweepCompact}
- G1 Garbage Collector
  - `jvm.memory.used`: {jvm.memory.pool.name=G1 Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=G1 Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=G1 Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.committed`: {jvm.memory.pool.name=G1 Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=G1 Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=G1 Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.limit`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=G1 Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.used_after_last_gc`: {jvm.memory.pool.name=G1 Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=G1 Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=G1 Old Gen,jvm.memory.type=heap}
  - `jvm.gc.duration`: {jvm.gc.action=end of minor GC,jvm.gc.name=G1 Young Generation}, {jvm.gc.action=end of major GC,jvm.gc.name=G1 Old Generation}
- Parallel Garbage Collector
  - `jvm.memory.used`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=PS Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.committed`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=PS Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.limit`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=PS Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.used_after_last_gc`: {jvm.memory.pool.name=PS Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Old Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=PS Eden Space,jvm.memory.type=heap}
  - `jvm.gc.duration`: {jvm.gc.action=end of major GC,jvm.gc.name=PS MarkSweep}, {jvm.gc.action=end of minor GC,jvm.gc.name=PS Scavenge}
- Serial Garbage Collector
  - `jvm.memory.used`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.committed`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}
  - `jvm.memory.limit`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Survivor Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.used_after_last_gc`: {jvm.memory.pool.name=Tenured Gen,jvm.memory.type=heap}, {jvm.memory.pool.name=Eden Space,jvm.memory.type=heap}, {jvm.memory.pool.name=Survivor Space,jvm.memory.type=heap}
  - `jvm.gc.duration`: {jvm.gc.action=end of minor GC,jvm.gc.name=Copy}, {jvm.gc.action=end of major GC,jvm.gc.name=MarkSweepCompact}
- Shenandoah Garbage Collector
  - `jvm.memory.used`: {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}, {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Shenandoah,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.committed`: {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}, {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Shenandoah,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.limit`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=Shenandoah,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.used_after_last_gc`: {jvm.memory.pool.name=Shenandoah,jvm.memory.type=heap}
  - `jvm.gc.duration`: {jvm.gc.action=end of GC cycle,jvm.gc.name=Shenandoah Cycles}, {jvm.gc.action=end of GC pause,jvm.gc.name=Shenandoah Pauses}
- Z Garbage Collector
  - `jvm.memory.used`: {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}, {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=ZHeap,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.committed`: {jvm.memory.pool.name=Metaspace,jvm.memory.type=non_heap}, {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=ZHeap,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.limit`: {jvm.memory.pool.name=CodeCache,jvm.memory.type=non_heap}, {jvm.memory.pool.name=ZHeap,jvm.memory.type=heap}, {jvm.memory.pool.name=Compressed Class Space,jvm.memory.type=non_heap}
  - `jvm.memory.used_after_last_gc`: {jvm.memory.pool.name=ZHeap,jvm.memory.type=heap}
  - `jvm.gc.duration`: {jvm.gc.action=end of GC cycle,jvm.gc.name=ZGC Cycles}, {jvm.gc.action=end of GC pause,jvm.gc.name=ZGC Pauses}
