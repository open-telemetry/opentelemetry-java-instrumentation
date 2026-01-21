/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#rpc-server">RPC
 * server metrics</a>.
 */
public final class RpcServerMetrics implements OperationListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  private static final ContextKey<RpcServerMetrics.State> RPC_SERVER_REQUEST_METRICS_STATE =
      ContextKey.named("rpc-server-request-metrics-state");

  private static final Logger logger = Logger.getLogger(RpcServerMetrics.class.getName());

  @Nullable private final DoubleHistogram oldServerDurationHistogram;
  @Nullable private final DoubleHistogram stableServerDurationHistogram;
  private final LongHistogram oldServerRequestSize;
  private final LongHistogram oldServerResponseSize;

  private RpcServerMetrics(Meter meter) {
    // Old metric (milliseconds)
    if (SemconvStability.emitOldRpcSemconv()) {
      DoubleHistogramBuilder oldDurationBuilder =
          meter
              .histogramBuilder("rpc.server.duration")
              .setDescription("The duration of an inbound RPC invocation.")
              .setUnit("ms");
      RpcMetricsAdvice.applyServerDurationAdvice(oldDurationBuilder, false);
      oldServerDurationHistogram = oldDurationBuilder.build();
    } else {
      oldServerDurationHistogram = null;
    }

    // Stable metric (seconds)
    if (SemconvStability.emitStableRpcSemconv()) {
      DoubleHistogramBuilder stableDurationBuilder =
          meter
              .histogramBuilder("rpc.server.call.duration")
              .setDescription("Measures the duration of inbound remote procedure calls (RPC).")
              .setUnit("s");
      RpcMetricsAdvice.applyServerDurationAdvice(stableDurationBuilder, true);
      stableServerDurationHistogram = stableDurationBuilder.build();
    } else {
      stableServerDurationHistogram = null;
    }

    LongHistogramBuilder requestSizeBuilder =
        meter
            .histogramBuilder("rpc.server.request.size")
            .setUnit("By")
            .setDescription("Measures the size of RPC request messages (uncompressed).")
            .ofLongs();
    RpcMetricsAdvice.applyDeprecatedServerRequestSizeAdvice(requestSizeBuilder);
    oldServerRequestSize = requestSizeBuilder.build();

    LongHistogramBuilder responseSizeBuilder =
        meter
            .histogramBuilder("rpc.server.response.size")
            .setUnit("By")
            .setDescription("Measures the size of RPC response messages (uncompressed).")
            .ofLongs();
    RpcMetricsAdvice.applyDeprecatedServerRequestSizeAdvice(responseSizeBuilder);
    oldServerResponseSize = responseSizeBuilder.build();
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
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        RPC_SERVER_REQUEST_METRICS_STATE,
        new AutoValue_RpcServerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(RPC_SERVER_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record RPC request metrics.",
          context);
      return;
    }
    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    double durationNanos = endNanos - state.startTimeNanos();

    // Record to old histogram (milliseconds)
    if (oldServerDurationHistogram != null) {
      oldServerDurationHistogram.record(
          durationNanos / NANOS_PER_MS,
          SemconvStability.getOldRpcMetricAttributes(attributes),
          context);
    }

    // Record to stable histogram (seconds)
    if (stableServerDurationHistogram != null) {
      stableServerDurationHistogram.record(durationNanos / NANOS_PER_S, attributes, context);
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      Long rpcServerRequestBodySize = attributes.get(RpcSizeAttributesExtractor.RPC_REQUEST_SIZE);
      if (rpcServerRequestBodySize != null) {
        oldServerRequestSize.record(
            rpcServerRequestBodySize,
            SemconvStability.getOldRpcMetricAttributes(attributes),
            context);
      }

      Long rpcServerResponseBodySize = attributes.get(RpcSizeAttributesExtractor.RPC_RESPONSE_SIZE);
      if (rpcServerResponseBodySize != null) {
        oldServerResponseSize.record(
            rpcServerResponseBodySize,
            SemconvStability.getOldRpcMetricAttributes(attributes),
            context);
      }
    }
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
