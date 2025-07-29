/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import java.util.function.Consumer;

// This file is generated using weaver. Do not edit manually.

/** Metric definitions generated from a Weaver model. Do not edit manually. */
public final class Metrics {

  public static ObservableDoubleGauge createSystemMemoryUtilization(
      Meter meter, Consumer<ObservableDoubleMeasurement> callback) {
    return meter
        .gaugeBuilder("system.memory.utilization")
        .setUnit("1")
        .setDescription("System memory utilization")
        .buildWithCallback(callback);
  }

  public static ObservableLongUpDownCounter createSystemMemoryUsage(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .upDownCounterBuilder("system.memory.usage")
        .setUnit("By")
        .setDescription("System memory usage")
        .buildWithCallback(callback);
  }

  public static ObservableLongCounter createSystemNetworkIo(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .counterBuilder("system.network.io")
        .setUnit("By")
        .setDescription("System network IO")
        .buildWithCallback(callback);
  }

  public static ObservableLongCounter createSystemNetworkPackets(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .counterBuilder("system.network.packets")
        .setUnit("{packets}")
        .setDescription("System network packets")
        .buildWithCallback(callback);
  }

  public static ObservableLongCounter createSystemNetworkErrors(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .counterBuilder("system.network.errors")
        .setUnit("{errors}")
        .setDescription("System network errors")
        .buildWithCallback(callback);
  }

  public static ObservableLongCounter createSystemDiskIo(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .counterBuilder("system.disk.io")
        .setUnit("By")
        .setDescription("System disk IO")
        .buildWithCallback(callback);
  }

  public static ObservableLongCounter createSystemDiskOperations(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .counterBuilder("system.disk.operations")
        .setUnit("{operations}")
        .setDescription("System disk operations")
        .buildWithCallback(callback);
  }

  public static ObservableLongUpDownCounter createRuntimeJavaMemory(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .upDownCounterBuilder("runtime.java.memory")
        .setUnit("By")
        .setDescription("Runtime Java memory")
        .buildWithCallback(callback);
  }

  public static ObservableLongGauge createRuntimeJavaCpuTime(
      Meter meter, Consumer<ObservableLongMeasurement> callback) {
    return meter
        .gaugeBuilder("runtime.java.cpu_time")
        .ofLongs()
        .setUnit("ms")
        .setDescription("Runtime Java CPU time")
        .buildWithCallback(callback);
  }

  private Metrics() {}
}
