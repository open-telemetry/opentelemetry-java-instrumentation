/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.buffer.DirectBufferStatisticsHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.classes.ClassesLoadedHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.container.ContainerConfigurationHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.cpu.ContextSwitchRateHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.cpu.LongLockHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.cpu.OverallCpuLoadHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection.G1GarbageCollectionHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection.OldGarbageCollectionHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection.YoungGarbageCollectionHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.memory.CodeCacheConfigurationHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.memory.G1HeapSummaryHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.memory.MetaspaceSummaryHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.memory.ObjectAllocationInNewTlabHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.memory.ObjectAllocationOutsideTlabHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.memory.ParallelHeapSummaryHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.network.NetworkReadHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.network.NetworkWriteHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.threads.ThreadCountHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.threads.VirtualThreadPinnedHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.threads.VirtualThreadSubmitFailedHandler;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;

final class HandlerRegistry {

  // These handlers cover only a subset of memory pools, so JMX must remain the source of the
  // metric.
  private static final Set<String> INCOMPLETE_JMX_REPLACEMENTS =
      Set.of(
          "jvm.memory.committed",
          "jvm.memory.limit",
          "jvm.memory.used",
          "jvm.memory.used_after_last_gc");
  private static final Set<String> INCOMPLETE_EXPERIMENTAL_JMX_REPLACEMENTS =
      Set.of(
          "jvm.buffer.count",
          "jvm.buffer.memory.limit",
          "jvm.buffer.memory.used",
          "jvm.memory.init");

  static List<RecordedEventHandler> getHandlers(
      Meter meter,
      Predicate<String> metricNamePredicate,
      boolean useLegacyCpuCountMetric,
      boolean requireCompleteJmxReplacement,
      boolean emitExperimentalJmxMetrics) {
    Set<String> availableEventNames = new HashSet<>();
    for (EventType eventType : FlightRecorder.getFlightRecorder().getEventTypes()) {
      availableEventNames.add(eventType.getName());
    }
    return getHandlers(
        meter,
        metricNamePredicate,
        useLegacyCpuCountMetric,
        requireCompleteJmxReplacement,
        emitExperimentalJmxMetrics,
        availableEventNames);
  }

  static List<RecordedEventHandler> getHandlers(
      Meter meter,
      Predicate<String> metricNamePredicate,
      boolean useLegacyCpuCountMetric,
      boolean requireCompleteJmxReplacement,
      boolean emitExperimentalJmxMetrics,
      Set<String> availableEventNames) {

    List<RecordedEventHandler> handlers = new ArrayList<>();
    Predicate<String> effectiveMetricNamePredicate =
        requireCompleteJmxReplacement
            ? metricNamePredicate.and(
                name ->
                    !INCOMPLETE_JMX_REPLACEMENTS.contains(name)
                        && !(emitExperimentalJmxMetrics
                            && INCOMPLETE_EXPERIMENTAL_JMX_REPLACEMENTS.contains(name)))
            : metricNamePredicate;
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = bean.getName();
      switch (name) {
        case "G1 Young Generation" -> {
          addIfAvailable(
              handlers,
              availableEventNames,
              "jdk.G1HeapSummary",
              () -> G1HeapSummaryHandler.create(meter, effectiveMetricNamePredicate));
          addIfAvailable(
              handlers,
              availableEventNames,
              "jdk.G1GarbageCollection",
              () -> G1GarbageCollectionHandler.create(meter, effectiveMetricNamePredicate));
        }

        case "Copy" ->
            addIfAvailable(
                handlers,
                availableEventNames,
                "jdk.YoungGarbageCollection",
                () ->
                    YoungGarbageCollectionHandler.create(
                        meter, effectiveMetricNamePredicate, name));

        case "PS Scavenge" -> {
          addIfAvailable(
              handlers,
              availableEventNames,
              "jdk.YoungGarbageCollection",
              () ->
                  YoungGarbageCollectionHandler.create(meter, effectiveMetricNamePredicate, name));
          addIfAvailable(
              handlers,
              availableEventNames,
              "jdk.PSHeapSummary",
              () -> ParallelHeapSummaryHandler.create(meter, effectiveMetricNamePredicate));
        }

        case "G1 Old Generation", "PS MarkSweep", "MarkSweepCompact" ->
            addIfAvailable(
                handlers,
                availableEventNames,
                "jdk.OldGarbageCollection",
                () ->
                    OldGarbageCollectionHandler.create(meter, effectiveMetricNamePredicate, name));

        default -> {}
      }
    }

    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.ObjectAllocationInNewTLAB",
        () -> ObjectAllocationInNewTlabHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.ObjectAllocationOutsideTLAB",
        () -> ObjectAllocationOutsideTlabHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.SocketRead",
        () -> NetworkReadHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.SocketWrite",
        () -> NetworkWriteHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.ThreadContextSwitchRate",
        () -> ContextSwitchRateHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.CPULoad",
        () -> OverallCpuLoadHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.ContainerConfiguration",
        () ->
            ContainerConfigurationHandler.create(
                meter, effectiveMetricNamePredicate, useLegacyCpuCountMetric));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.JavaMonitorWait",
        () -> LongLockHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.JavaThreadStatistics",
        () -> ThreadCountHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.VirtualThreadPinned",
        () -> VirtualThreadPinnedHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.VirtualThreadSubmitFailed",
        () -> VirtualThreadSubmitFailedHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.ClassLoadingStatistics",
        () -> ClassesLoadedHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.MetaspaceSummary",
        () -> MetaspaceSummaryHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.CodeCacheConfiguration",
        () -> CodeCacheConfigurationHandler.create(meter, effectiveMetricNamePredicate));
    addIfAvailable(
        handlers,
        availableEventNames,
        "jdk.DirectBufferStatistics",
        () -> DirectBufferStatisticsHandler.create(meter, effectiveMetricNamePredicate));

    return handlers;
  }

  private static void addIfAvailable(
      List<RecordedEventHandler> handlers,
      Set<String> availableEventNames,
      String eventName,
      HandlerFactory handlerFactory) {
    if (!availableEventNames.contains(eventName)) {
      return;
    }
    RecordedEventHandler handler = handlerFactory.create();
    if (handler != null) {
      handlers.add(handler);
    }
  }

  @FunctionalInterface
  private interface HandlerFactory {
    @Nullable
    RecordedEventHandler create();
  }

  private HandlerRegistry() {}
}
