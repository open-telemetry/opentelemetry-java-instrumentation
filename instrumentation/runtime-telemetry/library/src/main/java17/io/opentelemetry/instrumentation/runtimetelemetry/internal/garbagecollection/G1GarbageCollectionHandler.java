/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection;

import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_ACTION;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.DurationUtil;
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
public final class G1GarbageCollectionHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.G1GarbageCollection";
  private static final Attributes ATTR =
      Attributes.of(JVM_GC_NAME, "G1 Young Generation", JVM_GC_ACTION, Constants.END_OF_MINOR_GC);
  private final DoubleHistogram histogram;

  @Nullable
  public static G1GarbageCollectionHandler create(
      Meter meter, Predicate<String> metricNamePredicate) {
    return metricNamePredicate.test(Constants.METRIC_NAME_GC_DURATION)
        ? new G1GarbageCollectionHandler(meter)
        : null;
  }

  public G1GarbageCollectionHandler(Meter meter) {
    histogram =
        meter
            .histogramBuilder(Constants.METRIC_NAME_GC_DURATION)
            .setDescription(Constants.METRIC_DESCRIPTION_GC_DURATION)
            .setUnit(Constants.SECONDS)
            .build();
  }

  @Override
  public void accept(RecordedEvent ev) {
    histogram.record(DurationUtil.toSeconds(ev.getDuration()), ATTR);
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Set<String> getMetricNames() {
    return Set.of(Constants.METRIC_NAME_GC_DURATION);
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }
}
