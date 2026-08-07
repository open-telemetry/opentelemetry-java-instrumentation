/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JmxRuntimeMetricsFactory {
  public static List<AutoCloseable> buildObservables(
      boolean emitExperimentalTelemetry,
      boolean captureGcCause,
      Predicate<String> metricNamePredicate,
      Meter meter) {
    Meter filteringMeter = new FilteringMeter(meter, metricNamePredicate);
    List<AutoCloseable> observables = new ArrayList<>();
    observables.addAll(Classes.registerObservers(filteringMeter));
    observables.addAll(Cpu.registerObservers(filteringMeter));
    observables.addAll(CpuCount.registerObservers(filteringMeter));
    observables.addAll(GarbageCollector.registerObservers(filteringMeter, captureGcCause));
    observables.addAll(MemoryPools.registerObservers(filteringMeter));
    observables.addAll(Threads.registerObservers(filteringMeter));
    if (emitExperimentalTelemetry) {
      observables.addAll(BufferPools.registerObservers(filteringMeter));
      observables.addAll(SystemCpu.registerObservers(filteringMeter));
      observables.addAll(MemoryInit.registerObservers(filteringMeter));
      observables.addAll(FileDescriptor.registerObservers(filteringMeter));
    }
    return observables;
  }

  private JmxRuntimeMetricsFactory() {}
}
