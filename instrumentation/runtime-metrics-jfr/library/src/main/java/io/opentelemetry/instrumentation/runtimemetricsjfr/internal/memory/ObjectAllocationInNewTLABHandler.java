/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetricsjfr.internal.memory;

import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.ATTR_ARENA_NAME;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.ATTR_THREAD_NAME;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_DESCRIPTION_MEMORY_ALLOCATION;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_NAME_MEMORY_ALLOCATION;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetricsjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetricsjfr.internal.AbstractThreadDispatchingHandler;
import io.opentelemetry.instrumentation.runtimemetricsjfr.internal.ThreadGrouper;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class handles TLAB allocation JFR events, and delegates them to the actual per-thread
 * aggregators
 *
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public final class ObjectAllocationInNewTLABHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationInNewTLAB";

  private final LongHistogram histogram;

  public ObjectAllocationInNewTLABHandler(Meter meter, ThreadGrouper grouper) {
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
    return new PerThreadObjectAllocationInNewTLABHandler(histogram, threadName);
  }

  @Override
  public void close() {}

  /** This class aggregates all TLAB allocation JFR events for a single thread */
  private static class PerThreadObjectAllocationInNewTLABHandler
      implements Consumer<RecordedEvent> {
    private static final String TLAB_SIZE = "tlabSize";

    private final LongHistogram histogram;
    private final Attributes attributes;

    public PerThreadObjectAllocationInNewTLABHandler(LongHistogram histogram, String threadName) {
      this.histogram = histogram;
      this.attributes = Attributes.of(ATTR_THREAD_NAME, threadName, ATTR_ARENA_NAME, "TLAB");
    }

    @Override
    public void accept(RecordedEvent ev) {
      histogram.record(ev.getLong(TLAB_SIZE), attributes);
      // Probably too high a cardinality
      // ev.getClass("objectClass").getName();
    }
  }
}
