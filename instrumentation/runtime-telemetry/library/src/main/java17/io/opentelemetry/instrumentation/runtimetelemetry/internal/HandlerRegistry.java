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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

final class HandlerRegistry {

  private HandlerRegistry() {}

  @SuppressWarnings("StatementSwitchToExpressionSwitch")
  static List<RecordedEventHandler> getHandlers(
      Meter meter, Predicate<JfrFeature> featurePredicate, boolean useLegacyCpuCountMetric) {

    List<RecordedEventHandler> handlers = new ArrayList<RecordedEventHandler>();
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = bean.getName();
      switch (name) {
        case "G1 Young Generation":
          handlers.add(new G1HeapSummaryHandler(meter));
          handlers.add(new G1GarbageCollectionHandler(meter));
          break;

        case "Copy":
          handlers.add(new YoungGarbageCollectionHandler(meter, name));
          break;

        case "PS Scavenge":
          handlers.add(new YoungGarbageCollectionHandler(meter, name));
          handlers.add(new ParallelHeapSummaryHandler(meter));
          break;

        case "G1 Old Generation":
        case "PS MarkSweep":
        case "MarkSweepCompact":
          handlers.add(new OldGarbageCollectionHandler(meter, name));
          break;

        default:
          // If none of the above GCs are detected, no action.
      }
    }

    List<RecordedEventHandler> basicHandlers =
        List.of(
            new ObjectAllocationInNewTlabHandler(meter),
            new ObjectAllocationOutsideTlabHandler(meter),
            new NetworkReadHandler(meter),
            new NetworkWriteHandler(meter),
            new ContextSwitchRateHandler(meter),
            new OverallCpuLoadHandler(meter),
            new ContainerConfigurationHandler(meter, useLegacyCpuCountMetric),
            new LongLockHandler(meter),
            new ThreadCountHandler(meter),
            new ClassesLoadedHandler(meter),
            new MetaspaceSummaryHandler(meter),
            new CodeCacheConfigurationHandler(meter),
            new DirectBufferStatisticsHandler(meter));
    handlers.addAll(basicHandlers);

    // Filter and close disabled handlers
    Iterator<RecordedEventHandler> iter = handlers.iterator();
    while (iter.hasNext()) {
      RecordedEventHandler handler = iter.next();
      if (!featurePredicate.test(handler.getFeature())) {
        handler.close();
        iter.remove();
      }
    }

    return handlers;
  }
}
