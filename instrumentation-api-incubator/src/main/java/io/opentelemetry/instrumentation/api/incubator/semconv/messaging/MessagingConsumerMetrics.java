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
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final ContextKey<MessagingConsumerMetrics.State> MESSAGING_CONSUMER_METRICS_STATE =
      ContextKey.named("messaging-consumer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingConsumerMetrics.class.getName());

  private final boolean supportsStableSemconv;
  private final boolean consumedMessagesOnly;
  private final boolean enabled;
  @Nullable private final DoubleHistogram receiveDurationHistogram;
  @Nullable private final LongCounter receiveMessageCount;
  @Nullable private final DoubleHistogram clientOperationDurationHistogram;
  @Nullable private final LongCounter consumedMessagesCounter;

  private MessagingConsumerMetrics(Meter meter, boolean supportsStableSemconv) {
    this(meter, supportsStableSemconv, false);
  }

  private MessagingConsumerMetrics(
      Meter meter, boolean supportsStableSemconv, boolean consumedMessagesOnly) {
    this.supportsStableSemconv = supportsStableSemconv;
    this.consumedMessagesOnly = consumedMessagesOnly;
    boolean emitOldSemconv = !supportsStableSemconv || emitOldMessagingSemconv();
    boolean emitStableSemconv = supportsStableSemconv && emitStableMessagingSemconv();
    receiveDurationHistogram =
        !consumedMessagesOnly && emitOldSemconv ? buildReceiveDuration(meter) : null;
    receiveMessageCount =
        !consumedMessagesOnly && emitOldSemconv ? buildReceiveMessages(meter) : null;
    clientOperationDurationHistogram =
        !consumedMessagesOnly && emitStableSemconv ? buildClientOperationDuration(meter) : null;
    consumedMessagesCounter = emitStableSemconv ? buildConsumedMessages(meter) : null;
    enabled =
        receiveDurationHistogram != null
            || receiveMessageCount != null
            || clientOperationDurationHistogram != null
            || consumedMessagesCounter != null;
  }

  /** Returns metrics for extractors configured with {@link MessageOperation}. */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create(
        "messaging consumer", meter -> new MessagingConsumerMetrics(meter, false));
  }

  /** Returns metrics for extractors configured with {@link MessagingOperationType}. */
  public static OperationMetrics getForOperationType() {
    return OperationMetricsUtil.create(
        "messaging consumer", meter -> new MessagingConsumerMetrics(meter, true));
  }

  /** Returns only the stable consumed-messages metric for a delivered message. */
  public static OperationMetrics getConsumedMessages() {
    return OperationMetricsUtil.create(
        "messaging consumed messages", meter -> new MessagingConsumerMetrics(meter, true, true));
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    if (!enabled) {
      return context;
    }
    return context.with(
        MESSAGING_CONSUMER_METRICS_STATE,
        new AutoValue_MessagingConsumerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    if (!enabled) {
      return;
    }
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
    String operationType = attributes.get(MESSAGING_OPERATION_TYPE);
    boolean recordsLegacyReceive =
        !supportsStableSemconv
            || "receive".equals(attributes.get(MESSAGING_OPERATION))
            || MessagingOperationType.RECEIVE.value().equals(operationType);
    if (receiveDurationHistogram != null && recordsLegacyReceive) {
      receiveDurationHistogram.record(duration, attributes, context);
    }
    // Metric view attribute advice can only select keys statically. The concrete destination name
    // must be omitted when a template is available or the destination is temporary or anonymous,
    // so this conditional requirement must be enforced before recording.
    Attributes filteredAttributes =
        clientOperationDurationHistogram != null || consumedMessagesCounter != null
            ? MessagingMetricsAdvice.filterAttributes(attributes)
            : attributes;
    if (clientOperationDurationHistogram != null
        && !MessagingOperationType.PROCESS.value().equals(operationType)) {
      clientOperationDurationHistogram.record(duration, filteredAttributes, context);
    }

    Long batchMessageCount = getBatchMessageCount(state.startAttributes(), endAttributes);
    if (receiveMessageCount != null && recordsLegacyReceive) {
      long receiveMessagesCount = batchMessageCount == null ? 1 : batchMessageCount;
      if (!supportsStableSemconv || receiveMessagesCount > 0) {
        receiveMessageCount.add(receiveMessagesCount, attributes, context);
      }
    }
    if (consumedMessagesCounter != null
        && (consumedMessagesOnly || MessagingOperationType.RECEIVE.value().equals(operationType))) {
      long consumedMessagesCount =
          getConsumedMessagesCount(attributes, batchMessageCount, consumedMessagesOnly);
      if (consumedMessagesCount > 0) {
        consumedMessagesCounter.add(consumedMessagesCount, filteredAttributes, context);
      }
    }
  }

  @Nullable
  private static Long getBatchMessageCount(Attributes... attributesList) {
    for (Attributes attributes : attributesList) {
      Long value = attributes.get(MESSAGING_BATCH_MESSAGE_COUNT);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static long getConsumedMessagesCount(
      Attributes attributes, @Nullable Long batchMessageCount, boolean consumedMessagesOnly) {
    if (batchMessageCount != null) {
      return batchMessageCount;
    }
    return consumedMessagesOnly || attributes.get(ERROR_TYPE) == null ? 1 : 0;
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
