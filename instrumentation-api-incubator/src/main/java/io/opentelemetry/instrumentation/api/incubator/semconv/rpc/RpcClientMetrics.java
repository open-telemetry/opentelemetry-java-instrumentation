/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcCommonAttributesExtractor.OLD_RPC_METHOD_CONTEXT_KEY;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcCommonAttributesExtractor.RPC_METHOD;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
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
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#rpc-client">RPC
 * client metrics</a>.
 */
public final class RpcClientMetrics implements OperationListener {

  private static final double NANOS_PER_MS = MILLISECONDS.toNanos(1);
  private static final double NANOS_PER_S = SECONDS.toNanos(1);

  private static final ContextKey<RpcClientMetrics.State> RPC_CLIENT_REQUEST_METRICS_STATE =
      ContextKey.named("rpc-client-request-metrics-state");

  private static final Logger logger = Logger.getLogger(RpcClientMetrics.class.getName());

  @Nullable private final DoubleHistogram oldClientDurationHistogram;
  @Nullable private final DoubleHistogram stableClientDurationHistogram;
  @Nullable private final LongHistogram oldClientRequestSize;
  @Nullable private final LongHistogram oldClientResponseSize;

  private RpcClientMetrics(Meter meter) {
    // Old metric (milliseconds)
    if (emitOldRpcSemconv()) {
      DoubleHistogramBuilder oldDurationBuilder =
          meter
              .histogramBuilder("rpc.client.duration")
              .setDescription("The duration of an outbound RPC invocation.")
              .setUnit("ms");
      RpcMetricsAdvice.applyClientDurationAdvice(oldDurationBuilder, false);
      oldClientDurationHistogram = oldDurationBuilder.build();
    } else {
      oldClientDurationHistogram = null;
    }

    // Stable metric (seconds)
    if (emitStableRpcSemconv()) {
      DoubleHistogramBuilder stableDurationBuilder =
          meter
              .histogramBuilder("rpc.client.call.duration")
              .setDescription("Measures the duration of outbound remote procedure calls (RPC).")
              .setUnit("s");
      RpcMetricsAdvice.applyClientDurationAdvice(stableDurationBuilder, true);
      stableClientDurationHistogram = stableDurationBuilder.build();
    } else {
      stableClientDurationHistogram = null;
    }

    if (emitOldRpcSemconv()) {
      LongHistogramBuilder requestSizeBuilder =
          meter
              .histogramBuilder("rpc.client.request.size")
              .setUnit("By")
              .setDescription("Measures the size of RPC request messages (uncompressed).")
              .ofLongs();
      RpcMetricsAdvice.applyOldClientRequestSizeAdvice(requestSizeBuilder);
      oldClientRequestSize = requestSizeBuilder.build();

      LongHistogramBuilder responseSizeBuilder =
          meter
              .histogramBuilder("rpc.client.response.size")
              .setUnit("By")
              .setDescription("Measures the size of RPC response messages (uncompressed).")
              .ofLongs();
      RpcMetricsAdvice.applyOldClientRequestSizeAdvice(responseSizeBuilder);
      oldClientResponseSize = responseSizeBuilder.build();
    } else {
      oldClientRequestSize = null;
      oldClientResponseSize = null;
    }
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
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        RPC_CLIENT_REQUEST_METRICS_STATE,
        new AutoValue_RpcClientMetrics_State(
            startAttributes, startNanos, context.get(OLD_RPC_METHOD_CONTEXT_KEY)));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(RPC_CLIENT_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record RPC request metrics.",
          context);
      return;
    }
    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    double durationNanos = endNanos - state.startTimeNanos();

    if (emitOldRpcSemconv()) {
      Attributes oldAttributes = getOldAttributes(attributes, state);

      if (oldClientDurationHistogram != null) {
        oldClientDurationHistogram.record(durationNanos / NANOS_PER_MS, oldAttributes, context);
      }

      Long rpcClientRequestBodySize = attributes.get(RpcSizeAttributesExtractor.RPC_REQUEST_SIZE);
      if (rpcClientRequestBodySize != null) {
        oldClientRequestSize.record(rpcClientRequestBodySize, oldAttributes, context);
      }

      Long rpcClientResponseBodySize = attributes.get(RpcSizeAttributesExtractor.RPC_RESPONSE_SIZE);
      if (rpcClientResponseBodySize != null) {
        oldClientResponseSize.record(rpcClientResponseBodySize, oldAttributes, context);
      }
    }

    if (stableClientDurationHistogram != null) {
      stableClientDurationHistogram.record(durationNanos / NANOS_PER_S, attributes, context);
    }
  }

  private static Attributes getOldAttributes(Attributes attributes, State state) {
    String oldRpcMethod = state.oldRpcMethod();
    if (oldRpcMethod != null) {
      // dup mode: replace stable rpc.method with old value
      return attributes.toBuilder().put(RPC_METHOD, oldRpcMethod).build();
    }
    return attributes;
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();

    @Nullable
    abstract String oldRpcMethod();
  }
}
