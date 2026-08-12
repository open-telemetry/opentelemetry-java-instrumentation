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
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkReadHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.SocketRead";
  private static final String BYTES_READ = "bytesRead";

  private final Set<String> metricNames;
  @Nullable private final LongHistogram bytesHistogram;
  @Nullable private final DoubleHistogram durationHistogram;
  private final Attributes attributes;

  @Nullable
  public static NetworkReadHandler create(Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate,
            Constants.METRIC_NAME_NETWORK_BYTES,
            Constants.METRIC_NAME_NETWORK_DURATION);
    return metricNames.isEmpty() ? null : new NetworkReadHandler(meter, metricNames);
  }

  private NetworkReadHandler(Meter meter, Set<String> metricNames) {
    this.metricNames = metricNames;
    bytesHistogram =
        metricNames.contains(Constants.METRIC_NAME_NETWORK_BYTES)
            ? meter
                .histogramBuilder(Constants.METRIC_NAME_NETWORK_BYTES)
                .setDescription(Constants.METRIC_DESCRIPTION_NETWORK_BYTES)
                .setUnit(Constants.BYTES)
                .ofLongs()
                .build()
            : null;
    durationHistogram =
        metricNames.contains(Constants.METRIC_NAME_NETWORK_DURATION)
            ? meter
                .histogramBuilder(Constants.METRIC_NAME_NETWORK_DURATION)
                .setDescription(Constants.METRIC_DESCRIPTION_NETWORK_DURATION)
                .setUnit(Constants.SECONDS)
                .build()
            : null;
    attributes = Attributes.of(Constants.ATTR_NETWORK_MODE, Constants.NETWORK_MODE_READ);
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Set<String> getMetricNames() {
    return metricNames;
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (bytesHistogram != null) {
      bytesHistogram.record(ev.getLong(BYTES_READ), attributes);
    }
    if (durationHistogram != null) {
      durationHistogram.record(DurationUtil.toSeconds(ev.getDuration()), attributes);
    }
  }
}
