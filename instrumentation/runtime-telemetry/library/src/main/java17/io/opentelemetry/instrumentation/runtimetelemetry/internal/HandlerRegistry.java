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

  static List<RecordedEventHandler> getHandlers(
      Meter meter,
      Predicate<String> metricNamePredicate,
      boolean useLegacyCpuCountMetric,
      boolean requireCompleteJmxReplacement) {
    Set<String> availableEventNames = new HashSet<>();
    for (EventType eventType : FlightRecorder.getFlightRecorder().getEventTypes()) {
      availableEventNames.add(eventType.getName());
    }
    return getHandlers(
        meter,
        metricNamePredicate,
        useLegacyCpuCountMetric,
        requireCompleteJmxReplacement,
        availableEventNames);
  }

  static List<RecordedEventHandler> getHandlers(
      Meter meter,
      Predicate<String> metricNamePredicate,
      boolean useLegacyCpuCountMetric,
      boolean requireCompleteJmxReplacement,
      Set<String> availableEventNames) {

    List<RecordedEventHandler> handlers = new ArrayList<>();
    Predicate<String> effectiveMetricNamePredicate =
        requireCompleteJmxReplacement
            ? metricNamePredicate.and(name -> !INCOMPLETE_JMX_REPLACEMENTS.contains(name))
            : metricNamePredicate;
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = bean.getName();
      switch (name) {
        case "G1 Young Generation" -> {
          addIfPresent(
              handlers,
              availableEventNames,
              G1HeapSummaryHandler.create(meter, effectiveMetricNamePredicate));
          addIfPresent(
              handlers,
              availableEventNames,
              G1GarbageCollectionHandler.create(meter, effectiveMetricNamePredicate));
        }

        case "Copy" ->
            addIfPresent(
                handlers,
                availableEventNames,
                YoungGarbageCollectionHandler.create(meter, effectiveMetricNamePredicate, name));

        case "PS Scavenge" -> {
          addIfPresent(
              handlers,
              availableEventNames,
              YoungGarbageCollectionHandler.create(meter, effectiveMetricNamePredicate, name));
          addIfPresent(
              handlers,
              availableEventNames,
              ParallelHeapSummaryHandler.create(meter, effectiveMetricNamePredicate));
        }

        case "G1 Old Generation", "PS MarkSweep", "MarkSweepCompact" ->
            addIfPresent(
                handlers,
                availableEventNames,
                OldGarbageCollectionHandler.create(meter, effectiveMetricNamePredicate, name));

        default -> {}
      }
    }

    addIfPresent(
        handlers,
        availableEventNames,
        ObjectAllocationInNewTlabHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        ObjectAllocationOutsideTlabHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        NetworkReadHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        NetworkWriteHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        ContextSwitchRateHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        OverallCpuLoadHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        ContainerConfigurationHandler.create(
            meter, effectiveMetricNamePredicate, useLegacyCpuCountMetric));
    addIfPresent(
        handlers, availableEventNames, LongLockHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        ThreadCountHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        VirtualThreadPinnedHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        VirtualThreadSubmitFailedHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        ClassesLoadedHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        MetaspaceSummaryHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        CodeCacheConfigurationHandler.create(meter, effectiveMetricNamePredicate));
    addIfPresent(
        handlers,
        availableEventNames,
        DirectBufferStatisticsHandler.create(meter, effectiveMetricNamePredicate));

    return handlers;
  }

  private static void addIfPresent(
      List<RecordedEventHandler> handlers,
      Set<String> availableEventNames,
      @Nullable RecordedEventHandler handler) {
    if (handler == null) {
      return;
    }
    if (!availableEventNames.contains(handler.getEventName())) {
      handler.close();
      return;
    }
    handlers.add(handler);
  }

  private HandlerRegistry() {}
}
