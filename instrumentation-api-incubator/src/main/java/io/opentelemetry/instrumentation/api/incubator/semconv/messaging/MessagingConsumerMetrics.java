/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
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
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.26.0/docs/messaging/messaging-metrics.md#consumer-metrics">Consumer
 * metrics</a>.
 */
public final class MessagingConsumerMetrics implements OperationListener {
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");
  private static final ContextKey<MessagingConsumerMetrics.State> MESSAGING_CONSUMER_METRICS_STATE =
      ContextKey.named("messaging-consumer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingConsumerMetrics.class.getName());

  private final DoubleHistogram receiveDurationHistogram;
  private final LongCounter receiveMessageCount;

  private MessagingConsumerMetrics(Meter meter) {
    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder("messaging.receive.duration")
            .setDescription("Measures the duration of receive operation.")
            .setExplicitBucketBoundariesAdvice(MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS)
            .setUnit("s");
    MessagingMetricsAdvice.applyReceiveDurationAdvice(durationBuilder);
    receiveDurationHistogram = durationBuilder.build();

    LongCounterBuilder longCounterBuilder =
        meter
            .counterBuilder("messaging.receive.messages")
            .setDescription("Measures the number of received messages.")
            .setUnit("{message}");
    MessagingMetricsAdvice.applyReceiveMessagesAdvice(longCounterBuilder);
    receiveMessageCount = longCounterBuilder.build();
  }

  public static OperationMetrics get() {
    return OperationMetricsUtil.create("messaging consumer", MessagingConsumerMetrics::new);
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        MESSAGING_CONSUMER_METRICS_STATE,
        new AutoValue_MessagingConsumerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    MessagingConsumerMetrics.State state = context.get(MESSAGING_CONSUMER_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record consumer receive metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    receiveDurationHistogram.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_S, attributes, context);

    long receiveMessagesCount = getReceiveMessagesCount(state.startAttributes(), endAttributes);
    receiveMessageCount.add(receiveMessagesCount, attributes, context);
  }

  private static long getReceiveMessagesCount(Attributes... attributesList) {
    for (Attributes attributes : attributesList) {
      Long value = attributes.get(MESSAGING_BATCH_MESSAGE_COUNT);
      if (value != null) {
        return value;
      }
    }
    return 1;
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
