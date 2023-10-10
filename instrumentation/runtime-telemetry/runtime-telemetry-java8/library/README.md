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
OpenTelemetry opentelemetry = // OpenTelemetry instance configured elsewhere

BufferPools.registerObservers(opentelemetry);
Classes.registerObservers(opentelemetry);
Cpu.registerObservers(opentelemetry);
MemoryPools.registerObservers(opentelemetry);
Threads.registerObservers(opentelemetry);
GarbageCollector.registerObservers(opentelemetry);
```

## Garbage Collector Dependent Metrics

The attributes reported on the memory metrics (`process.runtime.jvm.memory.*`) and gc metrics (`process.runtime.jvm.gc.*`) are dependent on the garbage collector used by the application, since each garbage collector organizes memory pools differently and has different strategies for reclaiming memory during garbage collection.

The following lists attributes reported for a variety of garbage collectors. Notice that attributes are not necessarily constant across `*.init`, `*.usage`, `*.committed`, and `*.limit` since not all memory pools report a limit.

- CMS Garbage Collector
  - `process.runtime.jvm.memory.init`: {pool=Compressed Class Space,type=non_heap}, {pool=Par Eden Space,type=heap}, {pool=Tenured Gen,type=heap}, {pool=Par Survivor Space,type=heap}, {pool=Code Cache,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.usage`: {pool=Compressed Class Space,type=non_heap}, {pool=Par Eden Space,type=heap}, {pool=Tenured Gen,type=heap}, {pool=Par Survivor Space,type=heap}, {pool=Code Cache,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.committed`: {pool=Compressed Class Space,type=non_heap}, {pool=Par Eden Space,type=heap}, {pool=Tenured Gen,type=heap}, {pool=Par Survivor Space,type=heap}, {pool=Code Cache,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.limit`: {pool=Compressed Class Space,type=non_heap}, {pool=Par Eden Space,type=heap}, {pool=Tenured Gen,type=heap}, {pool=Par Survivor Space,type=heap}, {pool=Code Cache,type=non_heap}
  - `process.runtime.jvm.memory.usage_after_last_gc`: {pool=Par Eden Space,type=heap}, {pool=Tenured Gen,type=heap}, {pool=Par Survivor Space,type=heap}
  - `process.runtime.jvm.gc.duration`: {action=end of minor GC,gc=ParNew}, {action=end of major GC,gc=MarkSweepCompact}
- G1 Garbage Collector
  - `process.runtime.jvm.memory.init`: {pool=G1 Survivor Space,type=heap}, {pool=G1 Eden Space,type=heap}, {pool=CodeCache,type=non_heap}, {pool=G1 Old Gen,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.usage`: {pool=G1 Survivor Space,type=heap}, {pool=G1 Eden Space,type=heap}, {pool=CodeCache,type=non_heap}, {pool=G1 Old Gen,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.committed`: {pool=G1 Survivor Space,type=heap}, {pool=G1 Eden Space,type=heap}, {pool=CodeCache,type=non_heap}, {pool=G1 Old Gen,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.limit`: {pool=CodeCache,type=non_heap}, {pool=G1 Old Gen,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage_after_last_gc`: {pool=G1 Survivor Space,type=heap}, {pool=G1 Eden Space,type=heap}, {pool=G1 Old Gen,type=heap}
  - `process.runtime.jvm.gc.duration`: {action=end of minor GC,gc=G1 Young Generation}, {action=end of major GC,gc=G1 Old Generation}
- Parallel Garbage Collector
  - `process.runtime.jvm.memory.init`: {pool=CodeCache,type=non_heap}, {pool=PS Survivor Space,type=heap}, {pool=PS Old Gen,type=heap}, {pool=PS Eden Space,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.usage`: {pool=CodeCache,type=non_heap}, {pool=PS Survivor Space,type=heap}, {pool=PS Old Gen,type=heap}, {pool=PS Eden Space,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.committed`: {pool=CodeCache,type=non_heap}, {pool=PS Survivor Space,type=heap}, {pool=PS Old Gen,type=heap}, {pool=PS Eden Space,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.limit`: {pool=CodeCache,type=non_heap}, {pool=PS Survivor Space,type=heap}, {pool=PS Old Gen,type=heap}, {pool=PS Eden Space,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage_after_last_gc`: {pool=PS Survivor Space,type=heap}, {pool=PS Old Gen,type=heap}, {pool=PS Eden Space,type=heap}
  - `process.runtime.jvm.gc.duration`: {action=end of major GC,gc=PS MarkSweep}, {action=end of minor GC,gc=PS Scavenge}
- Serial Garbage Collector
  - `process.runtime.jvm.memory.init`: {pool=CodeCache,type=non_heap}, {pool=Tenured Gen,type=heap}, {pool=Eden Space,type=heap}, {pool=Survivor Space,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.usage`: {pool=CodeCache,type=non_heap}, {pool=Tenured Gen,type=heap}, {pool=Eden Space,type=heap}, {pool=Survivor Space,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.committed`: {pool=CodeCache,type=non_heap}, {pool=Tenured Gen,type=heap}, {pool=Eden Space,type=heap}, {pool=Survivor Space,type=heap}, {pool=Compressed Class Space,type=non_heap}, {pool=Metaspace,type=non_heap}
  - `process.runtime.jvm.memory.limit`: {pool=CodeCache,type=non_heap}, {pool=Tenured Gen,type=heap}, {pool=Eden Space,type=heap}, {pool=Survivor Space,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage_after_last_gc`: {pool=Tenured Gen,type=heap}, {pool=Eden Space,type=heap}, {pool=Survivor Space,type=heap}
  - `process.runtime.jvm.gc.duration`: {action=end of minor GC,gc=Copy}, {action=end of major GC,gc=MarkSweepCompact}
- Shenandoah Garbage Collector
  - `process.runtime.jvm.memory.init`: {pool=Metaspace,type=non_heap}, {pool=CodeCache,type=non_heap}, {pool=Shenandoah,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage`: {pool=Metaspace,type=non_heap}, {pool=CodeCache,type=non_heap}, {pool=Shenandoah,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.committed`: {pool=Metaspace,type=non_heap}, {pool=CodeCache,type=non_heap}, {pool=Shenandoah,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.limit`: {pool=CodeCache,type=non_heap}, {pool=Shenandoah,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage_after_last_gc`: {pool=Shenandoah,type=heap}
  - `process.runtime.jvm.gc.duration`: {action=end of GC cycle,gc=Shenandoah Cycles}, {action=end of GC pause,gc=Shenandoah Pauses}
- Z Garbage Collector
  - `process.runtime.jvm.memory.init`: {pool=Metaspace,type=non_heap}, {pool=CodeCache,type=non_heap}, {pool=ZHeap,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage`: {pool=Metaspace,type=non_heap}, {pool=CodeCache,type=non_heap}, {pool=ZHeap,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.committed`: {pool=Metaspace,type=non_heap}, {pool=CodeCache,type=non_heap}, {pool=ZHeap,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.limit`: {pool=CodeCache,type=non_heap}, {pool=ZHeap,type=heap}, {pool=Compressed Class Space,type=non_heap}
  - `process.runtime.jvm.memory.usage_after_last_gc`: {pool=ZHeap,type=heap}
  - `process.runtime.jvm.gc.duration`: {action=end of GC cycle,gc=ZGC Cycles}, {action=end of GC pause,gc=ZGC Pauses}
