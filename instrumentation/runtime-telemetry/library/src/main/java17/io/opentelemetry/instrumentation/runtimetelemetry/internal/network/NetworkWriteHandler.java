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

// jdk.SocketWrite {
//        startTime = 20:22:57.161
//        duration = 87.4 ms
//        host = "mysql-staging-agentdb-2"
//        address = "10.31.1.15"
//        port = 3306
//        bytesWritten = 34 bytes
//        eventThread = "ActivityWriteDaemon" (javaThreadId = 252)
//        stackTrace = [
//        java.net.SocketOutputStream.socketWrite(byte[], int, int) line: 68
//        java.net.SocketOutputStream.write(byte[], int, int) line: 150
//        java.io.BufferedOutputStream.flushBuffer() line: 81
//        java.io.BufferedOutputStream.flush() line: 142
//        com.mysql.cj.protocol.a.SimplePacketSender.send(byte[], int, byte) line: 55
//        ...
//        ]
// }

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkWriteHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.SocketWrite";
  private static final String BYTES_WRITTEN = "bytesWritten";

  private final Set<String> metricNames;
  @Nullable private final LongHistogram bytesHistogram;
  @Nullable private final DoubleHistogram durationHistogram;
  private final Attributes attributes;

  @Nullable
  public static NetworkWriteHandler create(Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate,
            Constants.METRIC_NAME_NETWORK_BYTES,
            Constants.METRIC_NAME_NETWORK_DURATION);
    return metricNames.isEmpty() ? null : new NetworkWriteHandler(meter, metricNames);
  }

  private NetworkWriteHandler(Meter meter, Set<String> metricNames) {
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
    attributes = Attributes.of(Constants.ATTR_NETWORK_MODE, Constants.NETWORK_MODE_WRITE);
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
      bytesHistogram.record(ev.getLong(BYTES_WRITTEN), attributes);
    }
    if (durationHistogram != null) {
      durationHistogram.record(DurationUtil.toSeconds(ev.getDuration()), attributes);
    }
  }
}
