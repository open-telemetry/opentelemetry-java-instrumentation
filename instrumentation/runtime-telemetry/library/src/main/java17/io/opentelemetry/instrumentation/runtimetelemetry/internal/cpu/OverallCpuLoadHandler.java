/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.cpu;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
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
  private final Set<String> metricNames;
  private final boolean processSelected;
  private final boolean machineSelected;

  private volatile double process = 0;
  private volatile double machine = 0;

  @Nullable
  public static OverallCpuLoadHandler create(Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate, METRIC_NAME_PROCESS, METRIC_NAME_MACHINE);
    return metricNames.isEmpty() ? null : new OverallCpuLoadHandler(meter, metricNames);
  }

  private OverallCpuLoadHandler(Meter meter, Set<String> metricNames) {
    this.metricNames = metricNames;
    processSelected = metricNames.contains(METRIC_NAME_PROCESS);
    machineSelected = metricNames.contains(METRIC_NAME_MACHINE);
    if (processSelected) {
      observables.add(
          meter
              .gaugeBuilder(METRIC_NAME_PROCESS)
              .setDescription(METRIC_DESCRIPTION_PROCESS)
              .setUnit(Constants.UNIT_UTILIZATION)
              .buildWithCallback(measurement -> measurement.record(process)));
    }
    if (machineSelected) {
      observables.add(
          meter
              .gaugeBuilder(METRIC_NAME_MACHINE)
              .setDescription(METRIC_DESCRIPTION_MACHINE)
              .setUnit(Constants.UNIT_UTILIZATION)
              .buildWithCallback(measurement -> measurement.record(machine)));
    }
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (processSelected && ev.hasField(JVM_USER) && ev.hasField(JVM_SYSTEM)) {
      process = ev.getDouble(JVM_USER) + ev.getDouble(JVM_SYSTEM);
    }
    if (machineSelected && ev.hasField(MACHINE_TOTAL)) {
      machine = ev.getDouble(MACHINE_TOTAL);
    }
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Set<String> getMetricNames() {
    return metricNames;
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
