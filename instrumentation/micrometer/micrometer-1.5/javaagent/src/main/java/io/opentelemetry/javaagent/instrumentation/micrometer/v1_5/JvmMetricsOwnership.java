/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import io.micrometer.core.instrument.Meter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Selects supported standard JVM meters whose observations are owned by agent JMX telemetry.
 * Complementary metrics and unknown names remain bridged; this is not a namespace filter.
 */
final class JvmMetricsOwnership implements Predicate<Meter.Id> {
  private static final Logger logger = Logger.getLogger(JvmMetricsOwnership.class.getName());

  private final boolean enabled;
  private final Set<String> kept;
  private final Set<String> registeredObservers;

  JvmMetricsOwnership(boolean enabled, Set<String> kept, Set<String> registeredObservers) {
    this.enabled = enabled;
    this.kept = Collections.unmodifiableSet(new HashSet<>(kept));
    this.registeredObservers = Collections.unmodifiableSet(new HashSet<>(registeredObservers));
    if (enabled && registeredObservers.isEmpty()) {
      // runtime telemetry is disabled, reports these metrics through JFR, or has not been
      // installed yet (agent listeners are delayed when the application uses a custom LogManager)
      logger.log(
          WARNING,
          "Micrometer JVM metrics ownership is enabled, but runtime telemetry has not registered"
              + " any supported JMX metrics; all Micrometer meters will be bridged.");
    }
  }

  @Override
  public boolean test(Meter.Id id) {
    if (!enabled || kept.contains(id.getName())) {
      return false;
    }
    String replacement = replacement(id);
    if (replacement == null || !registeredObservers.contains(replacement)) {
      return false;
    }
    if (logger.isLoggable(FINE)) {
      logger.log(
          FINE,
          "Not bridging Micrometer meter {0}, runtime telemetry reports {1}",
          new Object[] {id.getName(), replacement});
    }
    return true;
  }

  @Nullable
  private static String replacement(Meter.Id id) {
    // ClassLoaderMetrics (with either the Micrometer or the OpenTelemetry naming convention) reads
    // the same ClassLoadingMXBean values as runtime telemetry's Classes. Function counters have
    // Meter.Type.COUNTER.
    switch (id.getName()) {
      case "jvm.classes.loaded":
      case "jvm.class.count":
        return id.getType() == Meter.Type.GAUGE ? "jvm.class.count" : null;
      case "jvm.classes.loaded.count":
      case "jvm.class.loaded":
        return id.getType() == Meter.Type.COUNTER ? "jvm.class.loaded" : null;
      case "jvm.classes.unloaded":
      case "jvm.class.unloaded":
        return id.getType() == Meter.Type.COUNTER ? "jvm.class.unloaded" : null;
      // MemoryPoolMXBean.getUsage(): same pools and used/committed/max values. The agent
      // omits unavailable (-1) values and uses native pool/type attributes.
      case "jvm.memory.used":
      case "jvm.memory.committed":
        return id.getType() == Meter.Type.GAUGE ? id.getName() : null;
      case "jvm.memory.max":
      case "jvm.memory.limit":
        return id.getType() == Meter.Type.GAUGE ? "jvm.memory.limit" : null;
      // BufferPoolMXBean values; available only with experimental JMX buffer telemetry.
      case "jvm.buffer.count":
      case "jvm.buffer.memory.used":
        return id.getType() == Meter.Type.GAUGE ? id.getName() : null;
      case "jvm.buffer.total.capacity":
        return id.getType() == Meter.Type.GAUGE ? "jvm.buffer.memory.limit" : null;
      // Native thread counts partition live platform threads by state and daemon flag.
      // Peak, total-started, deadlocked and virtual-thread observations are complementary.
      case "jvm.threads.live":
      case "jvm.threads.daemon":
      case "jvm.threads.states":
      case "jvm.thread.count":
        return id.getType() == Meter.Type.GAUGE ? "jvm.thread.count" : null;
      // ProcessorMetrics with OpenTelemetry conventions reads the same OS bean values.
      case "system.cpu.count":
      case "jvm.cpu.count":
        return id.getType() == Meter.Type.GAUGE ? "jvm.cpu.count" : null;
      case "process.cpu.usage":
      case "jvm.cpu.recent_utilization":
        return id.getType() == Meter.Type.GAUGE ? "jvm.cpu.recent_utilization" : null;
      case "process.cpu.time":
      case "jvm.cpu.time":
        return id.getType() == Meter.Type.COUNTER ? "jvm.cpu.time" : null;
      // These OS observations require experimental JMX telemetry and platform support.
      case "system.cpu.usage":
        return id.getType() == Meter.Type.GAUGE ? "jvm.system.cpu.utilization" : null;
      case "system.load.average.1m":
        return id.getType() == Meter.Type.GAUGE ? "jvm.system.cpu.load_1m" : null;
      case "process.files.open":
        return id.getType() == Meter.Type.GAUGE ? "jvm.file_descriptor.count" : null;
      case "process.files.max":
        return id.getType() == Meter.Type.GAUGE ? "jvm.file_descriptor.limit" : null;
      // Both timers partition the same GC notifications collected by the native histogram.
      // Selecting ownership adopts native GC attributes, including its optional cause capture.
      case "jvm.gc.pause":
      case "jvm.gc.concurrent.phase.time":
        return id.getType() == Meter.Type.TIMER ? "jvm.gc.duration" : null;
      // In particular, jvm.memory.usage.after.gc is a utilization ratio, not the native
      // jvm.memory.used_after_last_gc byte count. GC allocation/promotion/live-data metrics,
      // compilation time and JVM info also remain bridged.
      default:
        return null;
    }
  }
}
