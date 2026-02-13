/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JmxRuntimeMetricsFactory {
  public static List<AutoCloseable> buildObservables(
      OpenTelemetry openTelemetry, boolean captureGcCause) {
    // Set up metrics gathered by JMX
    List<AutoCloseable> observables = new ArrayList<>();
    observables.addAll(Classes.registerObservers(openTelemetry));
    observables.addAll(Cpu.registerObservers(openTelemetry));
    observables.addAll(GarbageCollector.registerObservers(openTelemetry, captureGcCause));
    observables.addAll(MemoryPools.registerObservers(openTelemetry));
    observables.addAll(Threads.registerObservers(openTelemetry));
    return observables;
  }

  private JmxRuntimeMetricsFactory() {}
}
