/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.container;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContainerConfigurationHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.cpu.limit";
  private static final String EVENT_NAME = "jdk.ContainerConfiguration";
  private static final String EFFECTIVE_CPU_COUNT = "effectiveCpuCount";

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long value = 0L;

  public ContainerConfigurationHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME)
            .setUnit(Constants.ONE)
            .buildWithCallback(codm -> codm.record(value)));
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.CPU_COUNT_METRICS;
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (ev.hasField(EFFECTIVE_CPU_COUNT)) {
      value = ev.getLong(EFFECTIVE_CPU_COUNT);
    }
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
