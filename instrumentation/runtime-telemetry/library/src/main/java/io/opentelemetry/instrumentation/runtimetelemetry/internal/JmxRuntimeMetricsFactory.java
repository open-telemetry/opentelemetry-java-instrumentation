/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JmxRuntimeMetricsFactory {
  @SuppressWarnings("deprecation") // ExperimentalXxx classes are deprecated
  public static List<AutoCloseable> buildObservables(
      boolean emitExperimentalTelemetry,
      boolean captureGcCause,
      boolean preferJfrMetrics,
      Meter meter) {
    // Set up metrics gathered by JMX
    // When preferJfrMetrics is true, skip JMX metrics that have JFR equivalents
    List<AutoCloseable> observables = new ArrayList<>();
    if (!preferJfrMetrics) {
      observables.addAll(Classes.registerObservers(meter));
      observables.addAll(Cpu.registerObservers(meter));
      observables.addAll(GarbageCollector.registerObservers(meter, captureGcCause));
      observables.addAll(MemoryPools.registerObservers(meter));
      observables.addAll(Threads.registerObservers(meter));
    }
    if (emitExperimentalTelemetry) {
      if (!preferJfrMetrics) {
        observables.addAll(ExperimentalBufferPools.registerObservers(meter));
        observables.addAll(ExperimentalCpu.registerObservers(meter));
        observables.addAll(ExperimentalMemoryPools.registerObservers(meter));
      }
      // ExperimentalFileDescriptor has no JFR equivalent, always register
      observables.addAll(ExperimentalFileDescriptor.registerObservers(meter));
    }
    return observables;
  }

  private JmxRuntimeMetricsFactory() {}
}
