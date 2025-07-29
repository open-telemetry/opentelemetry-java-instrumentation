/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.memory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class handles TLAB allocation JFR events, and delegates them to the actual per-thread
 * aggregators
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ObjectAllocationInNewTlabHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationInNewTLAB";
  private static final String TLAB_SIZE = "tlabSize";

  private final LongHistogram histogram;
  private final Attributes attributes;

  public ObjectAllocationInNewTlabHandler(Meter meter) {
    histogram =
        meter
            .histogramBuilder(Constants.METRIC_NAME_MEMORY_ALLOCATION)
            .setDescription(Constants.METRIC_DESCRIPTION_MEMORY_ALLOCATION)
            .setUnit(Constants.BYTES)
            .ofLongs()
            .build();
    attributes = Attributes.of(Constants.ATTR_ARENA_NAME, "TLAB");
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.MEMORY_ALLOCATION_METRICS;
  }

  @Override
  public void accept(RecordedEvent ev) {
    histogram.record(ev.getLong(TLAB_SIZE), attributes);
    // Probably too high a cardinality
    // ev.getClass("objectClass").getName();
  }
}
