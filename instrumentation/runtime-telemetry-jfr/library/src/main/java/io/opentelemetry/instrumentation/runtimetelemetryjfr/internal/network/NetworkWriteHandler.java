/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.network;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_NETWORK_MODE;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_THREAD_NAME;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_DESCRIPTION_NETWORK_BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_DESCRIPTION_NETWORK_DURATION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_NAME_NETWORK_BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_NAME_NETWORK_DURATION;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.MILLISECONDS;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.NETWORK_MODE_WRITE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.AbstractThreadDispatchingHandler;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.DurationUtil;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.ThreadGrouper;
import java.util.function.Consumer;
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
public final class NetworkWriteHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.SocketWrite";

  private final LongHistogram bytesHistogram;
  private final DoubleHistogram durationHistogram;

  public NetworkWriteHandler(Meter meter, ThreadGrouper nameNormalizer) {
    super(nameNormalizer);
    bytesHistogram =
        meter
            .histogramBuilder(METRIC_NAME_NETWORK_BYTES)
            .setDescription(METRIC_DESCRIPTION_NETWORK_BYTES)
            .setUnit(BYTES)
            .ofLongs()
            .build();
    durationHistogram =
        meter
            .histogramBuilder(METRIC_NAME_NETWORK_DURATION)
            .setDescription(METRIC_DESCRIPTION_NETWORK_DURATION)
            .setUnit(MILLISECONDS)
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
    return new PerThreadNetworkWriteHandler(bytesHistogram, durationHistogram, threadName);
  }

  @Override
  public void close() {}

  private static final class PerThreadNetworkWriteHandler implements Consumer<RecordedEvent> {
    private static final String BYTES_WRITTEN = "bytesWritten";

    private final LongHistogram bytesHistogram;
    private final DoubleHistogram durationHistogram;
    private final Attributes attributes;

    private PerThreadNetworkWriteHandler(
        LongHistogram bytesHistogram, DoubleHistogram durationHistogram, String threadName) {
      this.bytesHistogram = bytesHistogram;
      this.durationHistogram = durationHistogram;
      this.attributes =
          Attributes.of(ATTR_THREAD_NAME, threadName, ATTR_NETWORK_MODE, NETWORK_MODE_WRITE);
    }

    @Override
    public void accept(RecordedEvent ev) {
      bytesHistogram.record(ev.getLong(BYTES_WRITTEN), attributes);
      durationHistogram.record(DurationUtil.toMillis(ev.getDuration()), attributes);
    }
  }
}
