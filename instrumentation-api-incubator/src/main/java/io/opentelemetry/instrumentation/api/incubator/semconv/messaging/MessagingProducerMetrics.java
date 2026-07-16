/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
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
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-metrics.md#producer-metrics">producer
 * metrics</a>.
 */
public final class MessagingProducerMetrics implements OperationListener {
  private static final double NANOS_PER_S = SECONDS.toNanos(1);

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");
  private static final ContextKey<MessagingProducerMetrics.State> MESSAGING_PRODUCER_METRICS_STATE =
      ContextKey.named("messaging-producer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingProducerMetrics.class.getName());

  @Nullable private final DoubleHistogram publishDurationHistogram;
  @Nullable private final DoubleHistogram clientOperationDurationHistogram;
  @Nullable private final LongCounter sentMessagesCounter;

  private MessagingProducerMetrics(Meter meter) {
    publishDurationHistogram = emitOldMessagingSemconv() ? buildPublishDuration(meter) : null;
    clientOperationDurationHistogram =
        emitStableMessagingSemconv() ? buildClientOperationDuration(meter) : null;
    sentMessagesCounter = emitStableMessagingSemconv() ? buildSentMessages(meter) : null;
  }

  public static OperationMetrics get() {
    return OperationMetricsUtil.create("messaging producer", MessagingProducerMetrics::new);
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
    double duration = (endNanos - state.startTimeNanos()) / NANOS_PER_S;

    if (publishDurationHistogram != null) {
      publishDurationHistogram.record(duration, attributes, context);
    }
    Attributes filteredAttributes =
        clientOperationDurationHistogram != null || sentMessagesCounter != null
            ? MessagingMetricsAdvice.filterAttributes(attributes)
            : attributes;
    if (clientOperationDurationHistogram != null) {
      clientOperationDurationHistogram.record(duration, filteredAttributes, context);
    }
    if (sentMessagesCounter != null) {
      Long batchMessageCount = attributes.get(MESSAGING_BATCH_MESSAGE_COUNT);
      sentMessagesCounter.add(
          batchMessageCount == null ? 1 : batchMessageCount, filteredAttributes, context);
    }
  }

  private static DoubleHistogram buildPublishDuration(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("messaging.publish.duration")
            .setDescription("Measures the duration of publish operation.")
            .setExplicitBucketBoundariesAdvice(MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS)
            .setUnit("s");
    MessagingMetricsAdvice.applyOldDurationAdvice(builder);
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
  }
}
