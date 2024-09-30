/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.garbagecollection;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.DurationUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class YoungGarbageCollectionHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.YoungGarbageCollection";

  private final DoubleHistogram histogram;
  private final Attributes attributes;

  public YoungGarbageCollectionHandler(Meter meter, String gc) {
    histogram =
        meter
            .histogramBuilder(Constants.METRIC_NAME_GC_DURATION)
            .setDescription(Constants.METRIC_DESCRIPTION_GC_DURATION)
            .setUnit(Constants.SECONDS)
            .build();
    // Set the attribute's GC based on which GC is being used.
    // G1 young collection is already handled by G1GarbageCollectionHandler.
    attributes =
        Attributes.of(
            Constants.ATTR_GC_NAME, gc, Constants.ATTR_GC_ACTION, Constants.END_OF_MINOR_GC);
  }

  @Override
  public void accept(RecordedEvent ev) {
    histogram.record(DurationUtil.toSeconds(ev.getDuration()), attributes);
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.GC_DURATION_METRICS;
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }
}
