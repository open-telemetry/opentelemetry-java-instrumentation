/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
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
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-metrics.md#metric-messagingprocessduration">message
 * processing metrics</a>.
 */
public final class MessagingProcessMetrics implements OperationListener {
  private static final double NANOS_PER_S = SECONDS.toNanos(1);

  private static final ContextKey<State> MESSAGING_PROCESS_METRICS_STATE =
      ContextKey.named("messaging-process-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingProcessMetrics.class.getName());

  @Nullable private final DoubleHistogram processDurationHistogram;

  private MessagingProcessMetrics(Meter meter) {
    processDurationHistogram = emitStableMessagingSemconv() ? buildProcessDuration(meter) : null;
  }

  public static OperationMetrics get() {
    return OperationMetricsUtil.create("messaging process", MessagingProcessMetrics::new);
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    if (processDurationHistogram == null) {
      return context;
    }
    return context.with(
        MESSAGING_PROCESS_METRICS_STATE,
        new AutoValue_MessagingProcessMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    if (processDurationHistogram == null) {
      return;
    }
    State state = context.get(MESSAGING_PROCESS_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record messaging process metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    processDurationHistogram.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_S,
        MessagingMetricsAdvice.filterAttributes(attributes),
        context);
  }

  private static DoubleHistogram buildProcessDuration(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("messaging.process.duration")
            .setDescription("Duration of processing operation.")
            .setExplicitBucketBoundariesAdvice(MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS)
            .setUnit("s");
    MessagingMetricsAdvice.applyProcessDurationAdvice(builder);
    return builder.build();
  }

  @AutoValue
  abstract static class State {
    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
