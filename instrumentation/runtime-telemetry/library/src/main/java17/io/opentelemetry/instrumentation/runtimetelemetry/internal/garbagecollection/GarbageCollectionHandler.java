/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection;

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
 * Records {@code jdk.GarbageCollection}, the only garbage collection event emitted by GraalVM
 * native images. The event does not tell minor and major collections apart, so no {@code
 * jvm.gc.action} attribute is recorded.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class GarbageCollectionHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.GarbageCollection";

  private final DoubleHistogram histogram;

  @Nullable
  public static GarbageCollectionHandler create(
      Meter meter, Predicate<String> metricNamePredicate) {
    return metricNamePredicate.test(Constants.METRIC_NAME_GC_DURATION)
        ? new GarbageCollectionHandler(meter)
        : null;
  }

  public GarbageCollectionHandler(Meter meter) {
    histogram =
        meter
            .histogramBuilder(Constants.METRIC_NAME_GC_DURATION)
            .setDescription(Constants.METRIC_DESCRIPTION_GC_DURATION)
            .setUnit(Constants.SECONDS)
            .build();
  }

  @Override
  public void accept(RecordedEvent ev) {
    histogram.record(
        DurationUtil.toSeconds(ev.getDuration()), Attributes.of(JVM_GC_NAME, ev.getString("name")));
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
