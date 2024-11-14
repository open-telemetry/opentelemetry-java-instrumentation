/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.cpu;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.AbstractThreadDispatchingHandler;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.DurationUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.ThreadGrouper;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LongLockHandler extends AbstractThreadDispatchingHandler {
  private static final String METRIC_NAME = "jvm.cpu.longlock";
  private static final String METRIC_DESCRIPTION = "Long lock times";
  private static final String EVENT_NAME = "jdk.JavaMonitorWait";

  private final DoubleHistogram histogram;

  public LongLockHandler(Meter meter, ThreadGrouper grouper) {
    super(grouper);
    histogram =
        meter
            .histogramBuilder(METRIC_NAME)
            .setDescription(METRIC_DESCRIPTION)
            .setUnit(Constants.SECONDS)
            .build();
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.LOCK_METRICS;
  }

  @Override
  public Consumer<RecordedEvent> createPerThreadSummarizer(String threadName) {
    return new PerThreadLongLockHandler(histogram, threadName);
  }

  @Override
  public Optional<Duration> getThreshold() {
    return Optional.empty();
  }

  private static class PerThreadLongLockHandler implements Consumer<RecordedEvent> {
    private static final String EVENT_THREAD = "eventThread";

    private final DoubleHistogram histogram;
    private final Attributes attributes;

    public PerThreadLongLockHandler(DoubleHistogram histogram, String threadName) {
      this.histogram = histogram;
      this.attributes = Attributes.of(Constants.ATTR_THREAD_NAME, threadName);
    }

    @Override
    public void accept(RecordedEvent recordedEvent) {
      if (recordedEvent.hasField(EVENT_THREAD)) {
        histogram.record(DurationUtil.toSeconds(recordedEvent.getDuration()), attributes);
      }
      // What about the class name in MONITOR_CLASS ?
      // We can get a stack trace from the thread on the event
      // var eventThread = recordedEvent.getThread(EVENT_THREAD);
    }
  }
}
