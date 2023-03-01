/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.cpu;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.HERTZ;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextSwitchRateHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "process.runtime.jvm.cpu.context_switch";
  private static final String EVENT_NAME = "jdk.ThreadContextSwitchRate";

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile double value = 0;

  public ContextSwitchRateHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME)
            .ofDoubles()
            .setUnit(HERTZ)
            .buildWithCallback(codm -> codm.record(value)));
  }

  @Override
  public void accept(RecordedEvent ev) {
    value = ev.getDouble("switchRate");
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.CONTEXT_SWITCH_METRICS;
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }

  @Override
  public void close() {
    closeObservables(observables);
  }
}
