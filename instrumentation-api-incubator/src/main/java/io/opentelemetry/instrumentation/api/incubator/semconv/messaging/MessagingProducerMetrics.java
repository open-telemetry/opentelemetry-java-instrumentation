/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

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
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-metrics.md#producer-metrics">producer
 * metrics</a>.
 */
public final class MessagingProducerMetrics implements OperationListener {
  private static final double NANOS_PER_S = SECONDS.toNanos(1);

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final ContextKey<MessagingProducerMetrics.State> MESSAGING_PRODUCER_METRICS_STATE =
      ContextKey.named("messaging-producer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingProducerMetrics.class.getName());

  @Nullable private final DoubleHistogram clientOperationDurationHistogram;
  private final LongCounter sentMessagesCounter;

  private MessagingProducerMetrics(Meter meter, boolean sentMessagesOnly) {
    clientOperationDurationHistogram =
        sentMessagesOnly ? null : MessagingMetricsAdvice.buildClientOperationDuration(meter);
    sentMessagesCounter = buildSentMessages(meter);
  }

  /** Returns metrics for extractors configured with {@link MessagingOperationType}. */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create(
        "messaging producer", meter -> new MessagingProducerMetrics(meter, false));
  }

  /** Returns only the sent-messages metric. */
  public static OperationMetrics getSentMessages() {
    return OperationMetricsUtil.create(
        "messaging sent messages", meter -> new MessagingProducerMetrics(meter, true));
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    MessagingOperationType operationType =
        MessagingOperationType.fromValue(startAttributes.get(MESSAGING_OPERATION_TYPE));
    boolean recordClientOperationDuration =
        clientOperationDurationHistogram != null
            && !MessagingTelemetryState.contains(
                context, operationType, MessagingTelemetrySignal.CLIENT_OPERATION_DURATION);
    boolean recordSentMessages =
        operationType == MessagingOperationType.SEND
            && !MessagingTelemetryState.contains(
                context, MessagingOperationType.SEND, MessagingTelemetrySignal.SENT_MESSAGES);
    if (recordClientOperationDuration) {
      context =
          MessagingTelemetryState.addIfEnabled(
              context, operationType, MessagingTelemetrySignal.CLIENT_OPERATION_DURATION);
    }
    if (recordSentMessages) {
      context =
          MessagingTelemetryState.addIfEnabled(
              context, MessagingOperationType.SEND, MessagingTelemetrySignal.SENT_MESSAGES);
    }
    return context.with(
        MESSAGING_PRODUCER_METRICS_STATE,
        new AutoValue_MessagingProducerMetrics_State(
            startAttributes, startNanos, recordClientOperationDuration, recordSentMessages));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    MessagingProducerMetrics.State state = context.get(MESSAGING_PRODUCER_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record messaging producer metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    double duration = (endNanos - state.startTimeNanos()) / NANOS_PER_S;

    Attributes filteredAttributes = MessagingMetricsAdvice.filterAttributes(attributes);
    if (clientOperationDurationHistogram != null && state.recordClientOperationDuration()) {
      clientOperationDurationHistogram.record(duration, filteredAttributes, context);
    }
    if (state.recordSentMessages()) {
      Long batchMessageCount = attributes.get(MESSAGING_BATCH_MESSAGE_COUNT);
      long sentMessagesCount = batchMessageCount == null ? 1 : batchMessageCount;
      if (sentMessagesCount > 0) {
        sentMessagesCounter.add(sentMessagesCount, filteredAttributes, context);
      }
    }
  }

  private static LongCounter buildSentMessages(Meter meter) {
    LongCounterBuilder builder =
        meter
            .counterBuilder("messaging.client.sent.messages")
            .setDescription("Number of messages producer attempted to send to the broker.")
            .setUnit("{message}");
    MessagingMetricsAdvice.applySentMessagesAdvice(builder);
    return builder.build();
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();

    abstract boolean recordClientOperationDuration();

    abstract boolean recordSentMessages();
  }
}
