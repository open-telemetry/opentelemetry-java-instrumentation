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
  @SuppressWarnings({"CatchingUnchecked", "deprecation"}) // until moved internal and undeprecated
  public static List<AutoCloseable> buildObservables(
      OpenTelemetry openTelemetry, boolean emitExperimentalTelemetry, boolean captureGcCause) {
    // Set up metrics gathered by JMX
    List<AutoCloseable> observables = new ArrayList<>();
    observables.addAll(
        io.opentelemetry.instrumentation.runtimemetrics.java8.Classes.registerObservers(
            openTelemetry));
    observables.addAll(
        io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu.registerObservers(openTelemetry));
    observables.addAll(
        io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector.registerObservers(
            openTelemetry, captureGcCause));
    observables.addAll(
        io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools.registerObservers(
            openTelemetry));
    observables.addAll(
        io.opentelemetry.instrumentation.runtimemetrics.java8.Threads.registerObservers(
            openTelemetry));
    if (emitExperimentalTelemetry) {
      observables.addAll(ExperimentalBufferPools.registerObservers(openTelemetry));
      observables.addAll(ExperimentalCpu.registerObservers(openTelemetry));
      observables.addAll(ExperimentalMemoryPools.registerObservers(openTelemetry));
      observables.addAll(ExperimentalFileDescriptor.registerObservers(openTelemetry));
    }
    return observables;
  }

  private JmxRuntimeMetricsFactory() {}
}
