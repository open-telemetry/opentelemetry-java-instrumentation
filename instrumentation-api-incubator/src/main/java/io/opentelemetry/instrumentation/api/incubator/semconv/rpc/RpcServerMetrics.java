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
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#rpc-server">RPC
 * server metrics</a>.
 */
public final class RpcServerMetrics implements OperationListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private final DoubleHistogram serverDurationHistogram;

  private RpcServerMetrics(Meter meter) {
    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder("rpc.server.duration")
            .setDescription("The duration of an inbound RPC invocation")
            .setUnit("ms");
    RpcMetricsAdvice.applyServerDurationAdvice(durationBuilder);
    serverDurationHistogram = durationBuilder.build();
  }

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * RpcServerMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create("rpc server", RpcServerMetrics::new);
  }

  @Override
  @SuppressWarnings("OtelCanIgnoreReturnValueSuggester")
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context;
  }

  @Override
  public void onEnd(Context context, Attributes startAndEndAttributes, long startNanos, long endNanos) {
    serverDurationHistogram.record(
        (endNanos - startNanos) / NANOS_PER_MS,
        startAndEndAttributes,
        context);
  }
}
