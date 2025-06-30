/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.cpu;

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
public final class LongLockHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.cpu.longlock";
  private static final String METRIC_DESCRIPTION = "Long lock times";
  private static final String EVENT_NAME = "jdk.JavaMonitorWait";

  private final DoubleHistogram histogram;
  private final Attributes attributes;

  public LongLockHandler(Meter meter) {
    histogram =
        meter
            .histogramBuilder(METRIC_NAME)
            .setDescription(METRIC_DESCRIPTION)
            .setUnit(Constants.SECONDS)
            .build();

    attributes = Attributes.empty();
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
  public void accept(RecordedEvent recordedEvent) {
    histogram.record(DurationUtil.toSeconds(recordedEvent.getDuration()), attributes);
    // What about the class name in MONITOR_CLASS ?
    // We can get a stack trace from the thread on the event
    // if (recordedEvent.hasField("eventThread")) {
    //  var eventThread = recordedEvent.getThread("eventThread");
    // }
  }

  @Override
  public Optional<Duration> getThreshold() {
    return Optional.empty();
  }
}
