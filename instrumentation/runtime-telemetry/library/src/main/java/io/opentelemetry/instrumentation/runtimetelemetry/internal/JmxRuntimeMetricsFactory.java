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
    List<AutoCloseable> observables = new ArrayList<>();
    observables.addAll(Classes.registerObservers(meter, metricNamePredicate));
    observables.addAll(Cpu.registerObservers(meter, metricNamePredicate));
    if (metricNamePredicate.test("jvm.cpu.count")) {
      observables.addAll(CpuCount.registerObservers(meter));
    }
    if (metricNamePredicate.test("jvm.gc.duration")) {
      observables.addAll(GarbageCollector.registerObservers(meter, captureGcCause));
    }
    observables.addAll(MemoryPools.registerObservers(meter, metricNamePredicate));
    if (metricNamePredicate.test("jvm.thread.count")) {
      observables.addAll(Threads.registerObservers(meter));
    }
    if (emitExperimentalTelemetry) {
      observables.addAll(BufferPools.registerObservers(meter, metricNamePredicate));
      observables.addAll(SystemCpu.registerObservers(meter, metricNamePredicate));
      if (metricNamePredicate.test("jvm.memory.init")) {
        observables.addAll(MemoryInit.registerObservers(meter));
      }
      observables.addAll(FileDescriptor.registerObservers(meter, metricNamePredicate));
    }
    return observables;
  }

  private JmxRuntimeMetricsFactory() {}
}
