/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.garbagecollection;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_ACTION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_GC;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.DURATION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.END_OF_MAJOR_GC;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_DESCRIPTION_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_NAME_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OldGarbageCollectionHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.OldGarbageCollection";

  private final LongHistogram histogram;
  private final Attributes attributes;

  public OldGarbageCollectionHandler(Meter meter, String gc) {
    histogram =
        meter
            .histogramBuilder(METRIC_NAME_GC_DURATION)
            .setDescription(METRIC_DESCRIPTION_GC_DURATION)
            .setUnit(MILLISECONDS)
            .ofLongs()
            .build();
    // Set the attribute's GC based on which GC is being used.
    attributes = Attributes.of(ATTR_GC, gc, ATTR_ACTION, END_OF_MAJOR_GC);
  }

  @Override
  public void accept(RecordedEvent ev) {
    histogram.record(ev.getLong(DURATION), attributes);
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
