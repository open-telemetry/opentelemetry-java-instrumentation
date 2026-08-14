/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.threads;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class VirtualThreadSubmitFailedHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.thread.virtual.submit_failed";
  private static final String METRIC_DESCRIPTION =
      "Number of times a virtual thread failed to be submitted to its scheduler.";
  private static final String EVENT_NAME = "jdk.VirtualThreadSubmitFailed";

  private final LongCounter counter;
  private final Attributes attributes;

  @Nullable
  public static VirtualThreadSubmitFailedHandler create(
      Meter meter, Predicate<String> metricNamePredicate) {
    return metricNamePredicate.test(METRIC_NAME)
        ? new VirtualThreadSubmitFailedHandler(meter)
        : null;
  }

  public VirtualThreadSubmitFailedHandler(Meter meter) {
    counter = meter.counterBuilder(METRIC_NAME).setDescription(METRIC_DESCRIPTION).build();

    attributes = Attributes.empty();
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
  public void accept(RecordedEvent recordedEvent) {
    counter.add(1, attributes);
  }

  @Override
  public Optional<Duration> getThreshold() {
    return Optional.empty();
  }
}
