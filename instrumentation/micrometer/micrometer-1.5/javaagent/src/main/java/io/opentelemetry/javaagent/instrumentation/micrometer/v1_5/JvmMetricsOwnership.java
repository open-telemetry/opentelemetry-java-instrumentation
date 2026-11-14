/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.Collections.unmodifiableMap;
import static java.util.logging.Level.FINE;

import io.micrometer.core.instrument.Meter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Selects standard Micrometer JVM meters that duplicate observations reported by agent runtime
 * telemetry. Complementary metrics and unknown names remain bridged.
 */
final class JvmMetricsOwnership implements Predicate<Meter.Id> {
  private static final Logger logger = Logger.getLogger(JvmMetricsOwnership.class.getName());

  private static final Map<String, Meter.Type> STABLE;
  private static final Map<String, Meter.Type> EXPERIMENTAL;

  static {
    Map<String, Meter.Type> stable = new HashMap<>();
    // ClassLoaderMetrics (with either the Micrometer or the OpenTelemetry naming convention) reads
    // the same ClassLoadingMXBean values as runtime telemetry's Classes. Function counters have
    // Meter.Type.COUNTER.
    stable.put("jvm.classes.loaded", Meter.Type.GAUGE);
    stable.put("jvm.class.count", Meter.Type.GAUGE);
    stable.put("jvm.classes.loaded.count", Meter.Type.COUNTER);
    stable.put("jvm.class.loaded", Meter.Type.COUNTER);
    stable.put("jvm.classes.unloaded", Meter.Type.COUNTER);
    stable.put("jvm.class.unloaded", Meter.Type.COUNTER);
    // MemoryPoolMXBean.getUsage(): same pools and used/committed/max values. The agent
    // omits unavailable (-1) values and uses native pool/type attributes.
    stable.put("jvm.memory.used", Meter.Type.GAUGE);
    stable.put("jvm.memory.committed", Meter.Type.GAUGE);
    stable.put("jvm.memory.max", Meter.Type.GAUGE);
    stable.put("jvm.memory.limit", Meter.Type.GAUGE);
    // Native thread counts partition live platform threads by state and daemon flag.
    // Peak, total-started, deadlocked and virtual-thread observations are complementary.
    stable.put("jvm.threads.live", Meter.Type.GAUGE);
    stable.put("jvm.threads.daemon", Meter.Type.GAUGE);
    stable.put("jvm.threads.states", Meter.Type.GAUGE);
    stable.put("jvm.thread.count", Meter.Type.GAUGE);
    // ProcessorMetrics with OpenTelemetry conventions reads the same OS bean values.
    stable.put("system.cpu.count", Meter.Type.GAUGE);
    stable.put("jvm.cpu.count", Meter.Type.GAUGE);
    stable.put("process.cpu.usage", Meter.Type.GAUGE);
    stable.put("jvm.cpu.recent_utilization", Meter.Type.GAUGE);
    stable.put("process.cpu.time", Meter.Type.COUNTER);
    stable.put("jvm.cpu.time", Meter.Type.COUNTER);
    // Both timers partition the same GC notifications collected by the native histogram.
    // Selecting ownership adopts native GC attributes, including GC cause capture.
    stable.put("jvm.gc.pause", Meter.Type.TIMER);
    stable.put("jvm.gc.concurrent.phase.time", Meter.Type.TIMER);
    // In particular, jvm.memory.usage.after.gc is a utilization ratio, not the native
    // jvm.memory.used_after_last_gc byte count. GC allocation/promotion/live-data metrics,
    // compilation time and JVM info also remain bridged.
    STABLE = unmodifiableMap(stable);

    // Replacements emitted only with runtime telemetry's experimental JMX metrics.
    Map<String, Meter.Type> experimental = new HashMap<>();
    experimental.put("jvm.buffer.count", Meter.Type.GAUGE);
    experimental.put("jvm.buffer.memory.used", Meter.Type.GAUGE);
    experimental.put("jvm.buffer.total.capacity", Meter.Type.GAUGE);
    experimental.put("system.cpu.usage", Meter.Type.GAUGE);
    experimental.put("system.load.average.1m", Meter.Type.GAUGE);
    experimental.put("process.files.open", Meter.Type.GAUGE);
    experimental.put("process.files.max", Meter.Type.GAUGE);
    EXPERIMENTAL = unmodifiableMap(experimental);
  }

  private final boolean emitExperimentalMetrics;

  JvmMetricsOwnership(boolean emitExperimentalMetrics) {
    this.emitExperimentalMetrics = emitExperimentalMetrics;
  }

  @Override
  public boolean test(Meter.Id id) {
    String name = id.getName();
    boolean suppressed =
        id.getType() == STABLE.get(name)
            || (emitExperimentalMetrics && id.getType() == EXPERIMENTAL.get(name));
    if (suppressed && logger.isLoggable(FINE)) {
      logger.log(FINE, "Not bridging Micrometer meter {0}, runtime telemetry reports it", name);
    }
    return suppressed;
  }
}
