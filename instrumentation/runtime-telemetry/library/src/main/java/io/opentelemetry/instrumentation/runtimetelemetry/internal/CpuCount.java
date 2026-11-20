/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.metrics.Meter;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Registers measurements that generate the JVM CPU count metric.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class CpuCount {

  // Visible for testing
  static final CpuCount INSTANCE = new CpuCount();

  /** Register observers for the JVM CPU count metric. */
  public static List<AutoCloseable> registerObservers(Meter meter) {
    return INSTANCE.registerObservers(meter, Runtime.getRuntime()::availableProcessors);
  }

  // Visible for testing
  List<AutoCloseable> registerObservers(Meter meter, IntSupplier availableProcessors) {
    AutoCloseable observable =
        meter
            .upDownCounterBuilder("jvm.cpu.count")
            .setDescription("Number of processors available to the Java virtual machine.")
            .setUnit("{cpu}")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(availableProcessors.getAsInt()));
    return singletonList(observable);
  }

  protected CpuCount() {}
}
