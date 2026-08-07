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
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

final class HandlerRegistry {

  private HandlerRegistry() {}

  static List<RecordedEventHandler> getHandlers(
      Meter meter, Predicate<String> metricNamePredicate, boolean useLegacyCpuCountMetric) {

    Meter filteringMeter = new FilteringMeter(meter, metricNamePredicate);
    List<RecordedEventHandler> handlers = new ArrayList<>();
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = bean.getName();
      switch (name) {
        case "G1 Young Generation" -> {
          handlers.add(new G1HeapSummaryHandler(filteringMeter));
          handlers.add(new G1GarbageCollectionHandler(filteringMeter));
        }

        case "Copy" -> handlers.add(new YoungGarbageCollectionHandler(filteringMeter, name));

        case "PS Scavenge" -> {
          handlers.add(new YoungGarbageCollectionHandler(filteringMeter, name));
          handlers.add(new ParallelHeapSummaryHandler(filteringMeter));
        }

        case "G1 Old Generation", "PS MarkSweep", "MarkSweepCompact" ->
            handlers.add(new OldGarbageCollectionHandler(filteringMeter, name));

        default -> {}
      }
    }

    List<RecordedEventHandler> basicHandlers =
        List.of(
            new ObjectAllocationInNewTlabHandler(filteringMeter),
            new ObjectAllocationOutsideTlabHandler(filteringMeter),
            new NetworkReadHandler(filteringMeter),
            new NetworkWriteHandler(filteringMeter),
            new ContextSwitchRateHandler(filteringMeter),
            new OverallCpuLoadHandler(filteringMeter),
            new ContainerConfigurationHandler(filteringMeter, useLegacyCpuCountMetric),
            new LongLockHandler(filteringMeter),
            new ThreadCountHandler(filteringMeter),
            new VirtualThreadPinnedHandler(filteringMeter),
            new VirtualThreadSubmitFailedHandler(filteringMeter),
            new ClassesLoadedHandler(filteringMeter),
            new MetaspaceSummaryHandler(filteringMeter),
            new CodeCacheConfigurationHandler(filteringMeter),
            new DirectBufferStatisticsHandler(filteringMeter));
    handlers.addAll(basicHandlers);

    // Drop handlers whose instruments were all filtered out.
    Iterator<RecordedEventHandler> iter = handlers.iterator();
    while (iter.hasNext()) {
      RecordedEventHandler handler = iter.next();
      if (handler.getMetricNames().stream().noneMatch(metricNamePredicate)) {
        handler.close();
        iter.remove();
      }
    }

    return handlers;
  }
}
