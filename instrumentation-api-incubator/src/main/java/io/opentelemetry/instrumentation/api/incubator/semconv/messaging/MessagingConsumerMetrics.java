/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;
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
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-metrics.md#consumer-metrics">consumer
 * metrics</a>.
 */
public final class MessagingConsumerMetrics implements OperationListener {
  private static final double NANOS_PER_S = SECONDS.toNanos(1);

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");
  private static final ContextKey<MessagingConsumerMetrics.State> MESSAGING_CONSUMER_METRICS_STATE =
      ContextKey.named("messaging-consumer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingConsumerMetrics.class.getName());

  @Nullable private final DoubleHistogram receiveDurationHistogram;
  @Nullable private final LongCounter receiveMessageCount;
  @Nullable private final DoubleHistogram clientOperationDurationHistogram;
  @Nullable private final LongCounter consumedMessagesCounter;

  private MessagingConsumerMetrics(Meter meter) {
    receiveDurationHistogram = emitOldMessagingSemconv() ? buildReceiveDuration(meter) : null;
    receiveMessageCount = emitOldMessagingSemconv() ? buildReceiveMessages(meter) : null;
    clientOperationDurationHistogram =
        emitStableMessagingSemconv() ? buildClientOperationDuration(meter) : null;
    consumedMessagesCounter = emitStableMessagingSemconv() ? buildConsumedMessages(meter) : null;
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
    double duration = (endNanos - state.startTimeNanos()) / NANOS_PER_S;
    if (receiveDurationHistogram != null) {
      receiveDurationHistogram.record(duration, attributes, context);
    }
    Attributes filteredAttributes =
        clientOperationDurationHistogram != null || consumedMessagesCounter != null
            ? MessagingMetricsAdvice.filterAttributes(attributes)
            : attributes;
    if (clientOperationDurationHistogram != null) {
      clientOperationDurationHistogram.record(duration, filteredAttributes, context);
    }

    long receiveMessagesCount = getReceiveMessagesCount(state.startAttributes(), endAttributes);
    if (receiveMessageCount != null) {
      receiveMessageCount.add(receiveMessagesCount, attributes, context);
    }
    if (consumedMessagesCounter != null) {
      consumedMessagesCounter.add(
          getConsumedMessagesCount(attributes, receiveMessagesCount), filteredAttributes, context);
    }
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

  private static long getConsumedMessagesCount(Attributes attributes, long receiveMessagesCount) {
    return attributes.get(MESSAGING_BATCH_MESSAGE_COUNT) == null
            && attributes.get(ERROR_TYPE) != null
        ? 0
        : receiveMessagesCount;
  }

  private static DoubleHistogram buildReceiveDuration(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("messaging.receive.duration")
            .setDescription("Measures the duration of receive operation.")
            .setExplicitBucketBoundariesAdvice(MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS)
            .setUnit("s");
    MessagingMetricsAdvice.applyOldDurationAdvice(builder);
    return builder.build();
  }

  private static LongCounter buildReceiveMessages(Meter meter) {
    LongCounterBuilder builder =
        meter
            .counterBuilder("messaging.receive.messages")
            .setDescription("Measures the number of received messages.")
            .setUnit("{message}");
    MessagingMetricsAdvice.applyOldMessagesAdvice(builder);
    return builder.build();
  }

  private static DoubleHistogram buildClientOperationDuration(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("messaging.client.operation.duration")
            .setDescription(
                "Duration of messaging operation initiated by a producer or consumer client.")
            .setExplicitBucketBoundariesAdvice(MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS)
            .setUnit("s");
    MessagingMetricsAdvice.applyClientOperationDurationAdvice(builder);
    return builder.build();
  }

  private static LongCounter buildConsumedMessages(Meter meter) {
    LongCounterBuilder builder =
        meter
            .counterBuilder("messaging.client.consumed.messages")
            .setDescription("Number of messages that were delivered to the application.")
            .setUnit("{message}");
    MessagingMetricsAdvice.applyConsumedMessagesAdvice(builder);
    return builder.build();
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
