# JVM Runtime Metrics

This module provides JVM runtime metrics as documented in the [semantic conventions](https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-runtime-telemetry-java8).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-runtime-telemetry-java8</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-runtime-telemetry-java8:OPENTELEMETRY_VERSION")
```

### Usage

Register observers for the desired runtime metrics:

```java
OpenTelemetry openTelemetry = // OpenTelemetry instance configured elsewhere

Classes.registerObservers(openTelemetry);
Cpu.registerObservers(openTelemetry);
MemoryPools.registerObservers(openTelemetry);
Threads.registerObservers(openTelemetry);
GarbageCollector.registerObservers(openTelemetry);
```

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
