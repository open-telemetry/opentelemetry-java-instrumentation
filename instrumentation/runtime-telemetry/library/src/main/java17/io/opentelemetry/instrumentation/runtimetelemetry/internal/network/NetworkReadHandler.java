/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.network;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.DurationUtil;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkReadHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.SocketRead";
  private static final String BYTES_READ = "bytesRead";

  private final LongHistogram bytesHistogram;
  private final DoubleHistogram durationHistogram;
  private final Attributes attributes;

  public NetworkReadHandler(Meter meter) {
    bytesHistogram =
        meter
            .histogramBuilder(Constants.METRIC_NAME_NETWORK_BYTES)
            .setDescription(Constants.METRIC_DESCRIPTION_NETWORK_BYTES)
            .setUnit(Constants.BYTES)
            .ofLongs()
            .build();
    durationHistogram =
        meter
            .histogramBuilder(Constants.METRIC_NAME_NETWORK_DURATION)
            .setDescription(Constants.METRIC_DESCRIPTION_NETWORK_DURATION)
            .setUnit(Constants.SECONDS)
            .build();
    attributes = Attributes.of(Constants.ATTR_NETWORK_MODE, Constants.NETWORK_MODE_READ);
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.NETWORK_IO_METRICS;
  }

  @Override
  public void accept(RecordedEvent ev) {
    bytesHistogram.record(ev.getLong(BYTES_READ), attributes);
    durationHistogram.record(DurationUtil.toSeconds(ev.getDuration()), attributes);
  }
}
