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
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final ContextKey<MessagingProducerMetrics.State> MESSAGING_PRODUCER_METRICS_STATE =
      ContextKey.named("messaging-producer-metrics-state");
  private static final Logger logger = Logger.getLogger(MessagingProducerMetrics.class.getName());

  private final boolean supportsStableSemconv;
  private final boolean enabled;
  @Nullable private final DoubleHistogram publishDurationHistogram;
  @Nullable private final DoubleHistogram clientOperationDurationHistogram;
  @Nullable private final LongCounter sentMessagesCounter;

  private MessagingProducerMetrics(Meter meter, Variant variant) {
    supportsStableSemconv = variant != Variant.LEGACY;
    boolean emitOldSemconv =
        variant == Variant.LEGACY
            || (variant == Variant.STABLE_AND_OLD && emitOldMessagingSemconv());
    boolean emitStableSemconv = supportsStableSemconv && emitStableMessagingSemconv();
    publishDurationHistogram = emitOldSemconv ? buildPublishDuration(meter) : null;
    clientOperationDurationHistogram =
        emitStableSemconv ? MessagingMetricsAdvice.buildClientOperationDuration(meter) : null;
    sentMessagesCounter = emitStableSemconv ? buildSentMessages(meter) : null;
    enabled =
        publishDurationHistogram != null
            || clientOperationDurationHistogram != null
            || sentMessagesCounter != null;
  }

  /**
   * Returns metrics for extractors configured with {@link MessageOperation}.
   *
   * <p>In 3.0 this method name will be reused for {@link #getForOperationType()}, which emits
   * different instruments, so callers must migrate rather than rely on this name continuing to
   * behave the same way.
   *
   * @deprecated Use {@link #getForOperationType()}. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public static OperationMetrics get() {
    return OperationMetricsUtil.create(
        "messaging producer", meter -> new MessagingProducerMetrics(meter, Variant.LEGACY));
  }

  /**
   * Returns metrics for extractors configured with {@link MessagingOperationType}, emitting only
   * the stable {@code messaging.client.*} instruments.
   */
  // will be renamed in 3.0 to get()
  public static OperationMetrics getForOperationType() {
    return OperationMetricsUtil.create(
        "messaging producer", meter -> new MessagingProducerMetrics(meter, Variant.STABLE));
  }

  /**
   * Returns metrics for extractors configured with {@link MessagingOperationType} that additionally
   * emit the deprecated {@code messaging.publish.duration} instrument.
   *
   * <p>Intended only for instrumentations that already emitted the pre-1.43 metrics before
   * migrating to {@link MessagingOperationType}, so that they keep emitting them until 3.0. New
   * instrumentations should use {@link #getForOperationType()} instead.
   */
  public static OperationMetrics getForOperationTypeWithOldMetrics() { // to be removed in 3.0
    return OperationMetricsUtil.create(
        "messaging producer", meter -> new MessagingProducerMetrics(meter, Variant.STABLE_AND_OLD));
  }

  @Override
  @CanIgnoreReturnValue
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    if (!enabled) {
      return context;
    }
    return context.with(
        MESSAGING_PRODUCER_METRICS_STATE,
        new AutoValue_MessagingProducerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    if (!enabled) {
      return;
    }
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

    if (publishDurationHistogram != null
        && (!supportsStableSemconv
            || "publish".equals(attributes.get(MESSAGING_OPERATION))
            || MessagingOperationType.SEND
                .value()
                .equals(attributes.get(MESSAGING_OPERATION_TYPE)))) {
      publishDurationHistogram.record(duration, attributes, context);
    }
    Attributes filteredAttributes =
        clientOperationDurationHistogram != null || sentMessagesCounter != null
            ? MessagingMetricsAdvice.filterAttributes(attributes)
            : attributes;
    if (clientOperationDurationHistogram != null) {
      clientOperationDurationHistogram.record(duration, filteredAttributes, context);
    }
    if (sentMessagesCounter != null
        && MessagingOperationType.SEND.value().equals(attributes.get(MESSAGING_OPERATION_TYPE))) {
      Long batchMessageCount = attributes.get(MESSAGING_BATCH_MESSAGE_COUNT);
      long sentMessagesCount = batchMessageCount == null ? 1 : batchMessageCount;
      if (sentMessagesCount > 0) {
        sentMessagesCounter.add(sentMessagesCount, filteredAttributes, context);
      }
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

  private enum Variant {
    /** Extractors configured with {@link MessageOperation}; old instruments only. */
    LEGACY,
    /** Extractors configured with {@link MessagingOperationType}; stable instruments only. */
    STABLE,
    /**
     * Extractors configured with {@link MessagingOperationType} that also keep emitting the
     * deprecated instruments.
     */
    STABLE_AND_OLD
  }
}
