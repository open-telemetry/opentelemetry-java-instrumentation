/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import java.util.concurrent.TimeUnit;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#rpc-client">RPC
 * client metrics</a>.
 */
public final class RpcClientMetrics implements OperationListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private final DoubleHistogram clientDurationHistogram;

  private RpcClientMetrics(Meter meter) {
    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder("rpc.client.duration")
            .setDescription("The duration of an outbound RPC invocation")
            .setUnit("ms");
    RpcMetricsAdvice.applyClientDurationAdvice(durationBuilder);
    clientDurationHistogram = durationBuilder.build();
  }

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * RpcClientMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create("rpc client", RpcClientMetrics::new);
  }

  @Override
  @SuppressWarnings("OtelCanIgnoreReturnValueSuggester")
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context;
  }

  @Override
  public void onEnd(
      Context context, Attributes startAndEndAttributes, long startNanos, long endNanos) {
    clientDurationHistogram.record(
        (endNanos - startNanos) / NANOS_PER_MS, startAndEndAttributes, context);
  }
}
