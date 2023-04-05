/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.memory;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_ARENA_NAME;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_THREAD_NAME;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_DESCRIPTION_MEMORY_ALLOCATION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_NAME_MEMORY_ALLOCATION;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.AbstractThreadDispatchingHandler;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.ThreadGrouper;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class handles all non-TLAB allocation JFR events, and delegates them to the actual
 * per-thread aggregators
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ObjectAllocationOutsideTlabHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationOutsideTLAB";

  private final LongHistogram histogram;

  public ObjectAllocationOutsideTlabHandler(Meter meter, ThreadGrouper grouper) {
    super(grouper);
    histogram =
        meter
            .histogramBuilder(METRIC_NAME_MEMORY_ALLOCATION)
            .setDescription(METRIC_DESCRIPTION_MEMORY_ALLOCATION)
            .setUnit(BYTES)
            .ofLongs()
            .build();
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
  public Consumer<RecordedEvent> createPerThreadSummarizer(String threadName) {
    return new PerThreadObjectAllocationOutsideTlabHandler(histogram, threadName);
  }

  /** This class aggregates all non-TLAB allocation JFR events for a single thread */
  private static class PerThreadObjectAllocationOutsideTlabHandler
      implements Consumer<RecordedEvent> {
    private static final String ALLOCATION_SIZE = "allocationSize";

    private final LongHistogram histogram;
    private final Attributes attributes;

    public PerThreadObjectAllocationOutsideTlabHandler(LongHistogram histogram, String threadName) {
      this.histogram = histogram;
      this.attributes = Attributes.of(ATTR_THREAD_NAME, threadName, ATTR_ARENA_NAME, "Main");
    }

    @Override
    public void accept(RecordedEvent ev) {
      histogram.record(ev.getLong(ALLOCATION_SIZE), attributes);
      // Probably too high a cardinality
      // ev.getClass("objectClass").getName();
    }
  }
}
