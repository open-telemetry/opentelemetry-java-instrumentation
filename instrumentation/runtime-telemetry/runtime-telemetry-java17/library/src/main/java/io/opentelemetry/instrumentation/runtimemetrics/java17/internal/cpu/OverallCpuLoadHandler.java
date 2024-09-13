/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.cpu;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OverallCpuLoadHandler implements RecordedEventHandler {
  private static final String METRIC_NAME_PROCESS = "jvm.cpu.recent_utilization";
  private static final String METRIC_NAME_MACHINE = "jvm.system.cpu.utilization";
  private static final String METRIC_DESCRIPTION_PROCESS =
      "Recent CPU utilization for the process as reported by the JVM.";
  private static final String METRIC_DESCRIPTION_MACHINE =
      "Recent CPU utilization for the whole system as reported by the JVM.";

  private static final String EVENT_NAME = "jdk.CPULoad";
  private static final String JVM_USER = "jvmUser";
  private static final String JVM_SYSTEM = "jvmSystem";
  private static final String MACHINE_TOTAL = "machineTotal";

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile double process = 0;
  private volatile double machine = 0;

  public OverallCpuLoadHandler(Meter meter) {
    observables.add(
        meter
            .gaugeBuilder(METRIC_NAME_PROCESS)
            .setDescription(METRIC_DESCRIPTION_PROCESS)
            .setUnit(Constants.UNIT_UTILIZATION)
            .buildWithCallback(measurement -> measurement.record(process)));
    observables.add(
        meter
            .gaugeBuilder(METRIC_NAME_MACHINE)
            .setDescription(METRIC_DESCRIPTION_MACHINE)
            .setUnit(Constants.UNIT_UTILIZATION)
            .buildWithCallback(measurement -> measurement.record(machine)));
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (ev.hasField(JVM_USER) && ev.hasField(JVM_SYSTEM)) {
      process = ev.getDouble(JVM_USER) + ev.getDouble(JVM_SYSTEM);
    }
    if (ev.hasField(MACHINE_TOTAL)) {
      machine = ev.getDouble(MACHINE_TOTAL);
    }
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.CPU_UTILIZATION_METRICS;
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
