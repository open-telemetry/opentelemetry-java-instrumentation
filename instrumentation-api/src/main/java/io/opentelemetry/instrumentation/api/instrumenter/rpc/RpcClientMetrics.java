/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static io.opentelemetry.instrumentation.api.instrumenter.rpc.MetricsView.applyClientView;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.instrumentation.api.instrumenter.RequestMetrics;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link RequestListener} which keeps track of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#rpc-client">RPC
 * client metrics</a>.
 */
@UnstableApi
public final class RpcClientMetrics implements RequestListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private static final ContextKey<RpcClientMetrics.State> RPC_CLIENT_REQUEST_METRICS_STATE =
      ContextKey.named("rpc-client-request-metrics-state");

  private static final Logger logger = Logger.getLogger(RpcClientMetrics.class.getName());

  private final DoubleHistogram clientDurationHistogram;

  private RpcClientMetrics(Meter meter) {
    clientDurationHistogram =
        meter
            .histogramBuilder("rpc.client.duration")
            .setDescription("The duration of an outbound RPC invocation")
            .setUnit("ms")
            .build();
  }

  /**
   * Returns a {@link RequestMetrics} which can be used to enable recording of {@link
   * RpcClientMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  @UnstableApi
  public static RequestMetrics get() {
    return RpcClientMetrics::new;
  }

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        RPC_CLIENT_REQUEST_METRICS_STATE,
        new AutoValue_RpcClientMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(RPC_CLIENT_REQUEST_METRICS_STATE);
    if (state == null) {
      if (logger.isLoggable(Level.FINE)) {
        logger.log(
            Level.FINE,
            "No state present when ending context "
                + context
                + ". Cannot record RPC request metrics.");
      }
      return;
    }
    clientDurationHistogram.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_MS,
        applyClientView(state.startAttributes(), endAttributes),
        context);
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
