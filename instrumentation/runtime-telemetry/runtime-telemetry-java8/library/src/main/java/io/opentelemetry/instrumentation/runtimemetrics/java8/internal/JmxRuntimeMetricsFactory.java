/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JmxRuntimeMetricsFactory {
  @SuppressWarnings("CatchingUnchecked")
  public static List<AutoCloseable> buildObservables(
      MeterProvider meterProvider, boolean enableExperimentalJmxTelemetry) {
    // Set up metrics gathered by JMX
    List<AutoCloseable> observables = new ArrayList<>();
    observables.addAll(Classes.registerObservers(meterProvider));
    observables.addAll(Cpu.registerObservers(meterProvider));
    observables.addAll(GarbageCollector.registerObservers(meterProvider));
    observables.addAll(MemoryPools.registerObservers(meterProvider));
    observables.addAll(Threads.registerObservers(meterProvider));
    if (enableExperimentalJmxTelemetry) {
      observables.addAll(ExperimentalBufferPools.registerObservers(meterProvider));
      observables.addAll(ExperimentalCpu.registerObservers(meterProvider));
      observables.addAll(ExperimentalMemoryPools.registerObservers(meterProvider));
    }
    return observables;
  }

  private JmxRuntimeMetricsFactory() {}
}
