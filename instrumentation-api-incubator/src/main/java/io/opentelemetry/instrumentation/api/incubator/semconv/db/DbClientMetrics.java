/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://opentelemetry.io/docs/specs/semconv/database/database-metrics/#metric-dbclientoperationduration">Database
 * client metrics</a>.
 *
 * @since 2.11.0
 */
public final class DbClientMetrics implements OperationListener {

  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  private static final ContextKey<State> DB_CLIENT_OPERATION_METRICS_STATE =
      ContextKey.named("db-client-metrics-state");

  private static final Logger logger = Logger.getLogger(DbClientMetrics.class.getName());

  /**
   * Returns an {@link OperationMetrics} instance which can be used to enable recording of {@link
   * DbClientMetrics}.
   *
   * @see InstrumenterBuilder#addOperationMetrics(OperationMetrics)
   */
  public static OperationMetrics get() {
    if (SemconvStability.emitStableDatabaseSemconv()) {
      return OperationMetricsUtil.create("database client", DbClientMetrics::new);
    }
    return meter -> OperationMetricsUtil.NOOP_OPERATION_LISTENER;
  }

  private final DoubleHistogram duration;

  private DbClientMetrics(Meter meter) {
    DoubleHistogramBuilder stableDurationBuilder =
        meter
            .histogramBuilder("db.client.operation.duration")
            .setUnit("s")
            .setDescription("Duration of database client operations.")
            .setExplicitBucketBoundariesAdvice(DbClientMetricsAdvice.DURATION_SECONDS_BUCKETS);
    DbClientMetricsAdvice.applyClientDurationAdvice(stableDurationBuilder);
    duration = stableDurationBuilder.build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        DB_CLIENT_OPERATION_METRICS_STATE,
        new AutoValue_DbClientMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(DB_CLIENT_OPERATION_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record database operation metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();

    duration.record((endNanos - state.startTimeNanos()) / NANOS_PER_S, attributes, context);
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
