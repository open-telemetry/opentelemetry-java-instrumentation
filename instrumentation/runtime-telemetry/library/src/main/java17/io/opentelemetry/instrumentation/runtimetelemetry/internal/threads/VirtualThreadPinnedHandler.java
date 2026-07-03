/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.threads;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.DurationUtil;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class VirtualThreadPinnedHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.thread.virtual.pinned";
  private static final String METRIC_DESCRIPTION = "Duration of virtual thread pinning.";
  private static final String EVENT_NAME = "jdk.VirtualThreadPinned";

  private final DoubleHistogram histogram;
  private final Attributes attributes;

  public VirtualThreadPinnedHandler(Meter meter) {
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
    return JfrFeature.VIRTUAL_THREAD_METRICS;
  }

  @Override
  public void accept(RecordedEvent recordedEvent) {
    histogram.record(DurationUtil.toSeconds(recordedEvent.getDuration()), attributes);
  }

  @Override
  public Optional<Duration> getThreshold() {
    return Optional.empty();
  }
}
