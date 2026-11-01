/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#rpc-server">RPC
 * server metrics</a>.
 */
public final class RpcServerMetrics implements OperationListener {

  private static final double NANOS_PER_S = SECONDS.toNanos(1);

  private static final ContextKey<RpcServerMetrics.State> RPC_SERVER_REQUEST_METRICS_STATE =
      ContextKey.named("rpc-server-request-metrics-state");

  private static final Logger logger = Logger.getLogger(RpcServerMetrics.class.getName());

  private final DoubleHistogram serverDurationHistogram;

  private RpcServerMetrics(Meter meter, List<Double> durationSecondsBucketBoundaries) {
    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder("rpc.server.call.duration")
            .setDescription("Measures the duration of an incoming Remote Procedure Call (RPC).")
            .setUnit("s")
            .setExplicitBucketBoundariesAdvice(durationSecondsBucketBoundaries);
    RpcMetricsAdvice.applyServerDurationAdvice(durationBuilder);
    serverDurationHistogram = durationBuilder.build();
  }

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * RpcServerMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return get(RpcMetricsAdvice.DURATION_SECONDS_BUCKETS);
  }

  /**
   * Returns a {@link OperationMetrics} which uses the given bucket boundaries for the duration
   * metric.
   *
   * @param durationSecondsBucketBoundaries the duration histogram bucket boundaries in seconds
   */
  public static OperationMetrics get(List<Double> durationSecondsBucketBoundaries) {
    return new Factory(durationSecondsBucketBoundaries);
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

    serverDurationHistogram.record(durationNanos / NANOS_PER_S, attributes, context);
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }

  private static final class Factory implements OperationMetrics {
    private final List<Double> durationSecondsBucketBoundaries;

    private Factory(List<Double> durationSecondsBucketBoundaries) {
      this.durationSecondsBucketBoundaries =
          Collections.unmodifiableList(new ArrayList<>(durationSecondsBucketBoundaries));
    }

    @Override
    public OperationListener create(Meter meter) {
      return OperationMetricsUtil.create(
              "rpc server", ignored -> new RpcServerMetrics(meter, durationSecondsBucketBoundaries))
          .create(meter);
    }
  }
}
