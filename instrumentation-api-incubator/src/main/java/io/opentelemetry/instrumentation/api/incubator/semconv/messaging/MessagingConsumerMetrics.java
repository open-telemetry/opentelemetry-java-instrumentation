/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState;
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
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  // Use RECEIVE as the coordination key because the counter records each delivered message once,
  // including when a PROCESS operation records it.
  private static final MessagingOperationType CONSUMED_MESSAGES_OPERATION =
      MessagingOperationType.RECEIVE;
  private static final Logger logger = Logger.getLogger(MessagingConsumerMetrics.class.getName());

  private final ContextKey<MessagingConsumerMetrics.State> messagingConsumerMetricsState =
      ContextKey.named("messaging-consumer-metrics-state");
  private final boolean consumedMessagesOnly;
  @Nullable private final DoubleHistogram clientOperationDurationHistogram;
  @Nullable private final LongCounter consumedMessagesCounter;

  private MessagingConsumerMetrics(Meter meter, Variant variant) {
    consumedMessagesOnly = variant == Variant.CONSUMED_MESSAGES_ONLY;
    clientOperationDurationHistogram =
        consumedMessagesOnly ? null : MessagingMetricsAdvice.buildClientOperationDuration(meter);
    consumedMessagesCounter =
        variant == Variant.CLIENT_OPERATION_DURATION_ONLY ? null : buildConsumedMessages(meter);
  }

  /** Returns metrics for extractors configured with {@link MessagingOperationType}. */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create(
        "messaging consumer", meter -> new MessagingConsumerMetrics(meter, Variant.ALL));
  }

  /** Returns only the client-operation-duration metric. */
  public static OperationMetrics getClientOperationDuration() {
    return OperationMetricsUtil.create(
        "messaging client operation duration",
        meter -> new MessagingConsumerMetrics(meter, Variant.CLIENT_OPERATION_DURATION_ONLY));
  }

  /** Returns only the consumed-messages metric for a delivered message. */
  public static OperationMetrics getConsumedMessages() {
    return OperationMetricsUtil.create(
        "messaging consumed messages",
        meter -> new MessagingConsumerMetrics(meter, Variant.CONSUMED_MESSAGES_ONLY));
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    MessagingOperationType operationType =
        MessagingOperationType.fromValue(startAttributes.get(MESSAGING_OPERATION_TYPE));
    boolean recordClientOperationDuration =
        clientOperationDurationHistogram != null
            && operationType != MessagingOperationType.PROCESS
            && !MessagingTelemetryState.contains(
                context, operationType, MessagingTelemetrySignal.CLIENT_OPERATION_DURATION);
    boolean recordConsumedMessages =
        consumedMessagesCounter != null
            && (consumedMessagesOnly || operationType == MessagingOperationType.RECEIVE)
            && !MessagingTelemetryState.contains(
                context, CONSUMED_MESSAGES_OPERATION, MessagingTelemetrySignal.CONSUMED_MESSAGES);
    if (recordClientOperationDuration) {
      context =
          MessagingTelemetryState.addIfEnabled(
              context, operationType, MessagingTelemetrySignal.CLIENT_OPERATION_DURATION);
    }
    if (recordConsumedMessages) {
      context =
          MessagingTelemetryState.addIfEnabled(
              context, CONSUMED_MESSAGES_OPERATION, MessagingTelemetrySignal.CONSUMED_MESSAGES);
    }
    return context.with(
        messagingConsumerMetricsState,
        new AutoValue_MessagingConsumerMetrics_State(
            startAttributes, startNanos, recordClientOperationDuration, recordConsumedMessages));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    MessagingConsumerMetrics.State state = context.get(messagingConsumerMetricsState);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record messaging consumer metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    double duration = (endNanos - state.startTimeNanos()) / NANOS_PER_S;
    // Metric view attribute advice can only select keys statically. The concrete destination name
    // must be omitted when a template is available or the destination is temporary or anonymous,
    // so this conditional requirement must be enforced before recording.
    Attributes filteredAttributes = MessagingMetricsAdvice.filterAttributes(attributes);
    if (clientOperationDurationHistogram != null && state.recordClientOperationDuration()) {
      clientOperationDurationHistogram.record(duration, filteredAttributes, context);
    }

    Long batchMessageCount = attributes.get(MESSAGING_BATCH_MESSAGE_COUNT);
    if (consumedMessagesCounter != null && state.recordConsumedMessages()) {
      long consumedMessagesCount =
          getConsumedMessagesCount(attributes, batchMessageCount, consumedMessagesOnly);
      if (consumedMessagesCount > 0) {
        consumedMessagesCounter.add(consumedMessagesCount, filteredAttributes, context);
      }
    }
  }

  private static long getConsumedMessagesCount(
      Attributes attributes, @Nullable Long batchMessageCount, boolean consumedMessagesOnly) {
    if (batchMessageCount != null) {
      return batchMessageCount;
    }
    return consumedMessagesOnly || attributes.get(ERROR_TYPE) == null ? 1 : 0;
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

    abstract boolean recordClientOperationDuration();

    abstract boolean recordConsumedMessages();
  }

  private enum Variant {
    ALL,
    /** Only the client-operation-duration histogram. */
    CLIENT_OPERATION_DURATION_ONLY,
    /** Only the consumed-messages counter, for a delivered message. */
    CONSUMED_MESSAGES_ONLY
  }
}
