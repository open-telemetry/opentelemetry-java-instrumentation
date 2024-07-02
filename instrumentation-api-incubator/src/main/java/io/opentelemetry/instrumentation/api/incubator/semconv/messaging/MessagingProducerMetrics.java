/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

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
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.26.0/docs/messaging/messaging-metrics.md#metric-messagingpublishduration">Producer
 * metrics</a>.
 */
public final class MessagingProducerMetrics implements OperationListener {
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  private static final ContextKey<MessagingProducerMetrics.State> MESSAGING_PRODUCER_METRICS_STATE =
      ContextKey.named("messaging-producer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingProducerMetrics.class.getName());

  private final DoubleHistogram publishDurationHistogram;

  private MessagingProducerMetrics(Meter meter) {
    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder("messaging.publish.duration")
            .setDescription("Measures the duration of publish operation.")
            .setExplicitBucketBoundariesAdvice(MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS)
            .setUnit("s");
    MessagingMetricsAdvice.applyPublishDurationAdvice(durationBuilder);
    publishDurationHistogram = durationBuilder.build();
  }

  public static OperationMetrics get() {
    return OperationMetricsUtil.create("messaging produce", MessagingProducerMetrics::new);
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        MESSAGING_PRODUCER_METRICS_STATE,
        new AutoValue_MessagingProducerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    MessagingProducerMetrics.State state = context.get(MESSAGING_PRODUCER_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record produce publish metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();

    publishDurationHistogram.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_S, attributes, context);
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
