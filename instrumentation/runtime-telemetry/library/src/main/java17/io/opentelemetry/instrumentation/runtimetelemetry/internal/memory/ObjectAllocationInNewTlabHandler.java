/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.memory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
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

  @Nullable
  public static ObjectAllocationInNewTlabHandler create(
      Meter meter, Predicate<String> metricNamePredicate) {
    return metricNamePredicate.test(Constants.METRIC_NAME_MEMORY_ALLOCATION)
        ? new ObjectAllocationInNewTlabHandler(meter)
        : null;
  }

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
  public Set<String> getMetricNames() {
    return Set.of(Constants.METRIC_NAME_MEMORY_ALLOCATION);
  }

  @Override
  public void accept(RecordedEvent ev) {
    histogram.record(ev.getLong(TLAB_SIZE), attributes);
    // Probably too high a cardinality
    // ev.getClass("objectClass").getName();
  }
}
