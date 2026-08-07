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
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

final class HandlerRegistry {

  private HandlerRegistry() {}

  static List<RecordedEventHandler> getHandlers(
      Meter meter, Predicate<String> metricNamePredicate, boolean useLegacyCpuCountMetric) {

    List<RecordedEventHandler> handlers = new ArrayList<>();
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = bean.getName();
      switch (name) {
        case "G1 Young Generation" -> {
          addIfPresent(handlers, G1HeapSummaryHandler.create(meter, metricNamePredicate));
          addIfPresent(handlers, G1GarbageCollectionHandler.create(meter, metricNamePredicate));
        }

        case "Copy" ->
            addIfPresent(
                handlers, YoungGarbageCollectionHandler.create(meter, metricNamePredicate, name));

        case "PS Scavenge" -> {
          addIfPresent(
              handlers, YoungGarbageCollectionHandler.create(meter, metricNamePredicate, name));
          addIfPresent(handlers, ParallelHeapSummaryHandler.create(meter, metricNamePredicate));
        }

        case "G1 Old Generation", "PS MarkSweep", "MarkSweepCompact" ->
            addIfPresent(
                handlers, OldGarbageCollectionHandler.create(meter, metricNamePredicate, name));

        default -> {}
      }
    }

    addIfPresent(handlers, ObjectAllocationInNewTlabHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, ObjectAllocationOutsideTlabHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, NetworkReadHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, NetworkWriteHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, ContextSwitchRateHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, OverallCpuLoadHandler.create(meter, metricNamePredicate));
    addIfPresent(
        handlers,
        ContainerConfigurationHandler.create(meter, metricNamePredicate, useLegacyCpuCountMetric));
    addIfPresent(handlers, LongLockHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, ThreadCountHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, VirtualThreadPinnedHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, VirtualThreadSubmitFailedHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, ClassesLoadedHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, MetaspaceSummaryHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, CodeCacheConfigurationHandler.create(meter, metricNamePredicate));
    addIfPresent(handlers, DirectBufferStatisticsHandler.create(meter, metricNamePredicate));

    return handlers;
  }

  private static void addIfPresent(
      List<RecordedEventHandler> handlers, @Nullable RecordedEventHandler handler) {
    if (handler != null) {
      handlers.add(handler);
    }
  }
}
