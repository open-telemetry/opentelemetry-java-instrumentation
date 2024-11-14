/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.network;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.AbstractThreadDispatchingHandler;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.DurationUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.ThreadGrouper;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkReadHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.SocketRead";

  private final LongHistogram bytesHistogram;
  private final DoubleHistogram durationHistogram;

  public NetworkReadHandler(Meter meter, ThreadGrouper nameNormalizer) {
    super(nameNormalizer);
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
  public Consumer<RecordedEvent> createPerThreadSummarizer(String threadName) {
    return new PerThreadNetworkReadHandler(bytesHistogram, durationHistogram, threadName);
  }

  private static class PerThreadNetworkReadHandler implements Consumer<RecordedEvent> {
    private static final String BYTES_READ = "bytesRead";

    private final LongHistogram bytesHistogram;
    private final DoubleHistogram durationHistogram;
    private final Attributes attributes;

    public PerThreadNetworkReadHandler(
        LongHistogram bytesHistogram, DoubleHistogram durationHistogram, String threadName) {
      this.bytesHistogram = bytesHistogram;
      this.durationHistogram = durationHistogram;
      this.attributes =
          Attributes.of(
              Constants.ATTR_THREAD_NAME,
              threadName,
              Constants.ATTR_NETWORK_MODE,
              Constants.NETWORK_MODE_READ);
    }

    @Override
    public void accept(RecordedEvent ev) {
      bytesHistogram.record(ev.getLong(BYTES_READ), attributes);
      durationHistogram.record(DurationUtil.toSeconds(ev.getDuration()), attributes);
    }
  }
}
