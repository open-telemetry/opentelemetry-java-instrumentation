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
public final class ContextSwitchRateHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.cpu.context_switch";
  private static final String EVENT_NAME = "jdk.ThreadContextSwitchRate";

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile double value = 0;

  @Nullable
  public static ContextSwitchRateHandler create(
      Meter meter, Predicate<String> metricNamePredicate) {
    return metricNamePredicate.test(METRIC_NAME) ? new ContextSwitchRateHandler(meter) : null;
  }

  public ContextSwitchRateHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME)
            .ofDoubles()
            .setUnit(Constants.HERTZ)
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
  public Set<String> getMetricNames() {
    return Set.of(METRIC_NAME);
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
