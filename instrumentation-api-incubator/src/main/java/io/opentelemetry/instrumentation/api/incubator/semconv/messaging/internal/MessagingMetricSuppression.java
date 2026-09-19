/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import javax.annotation.Nullable;

/**
 * The {@link MessagingTelemetrySignals} held by a {@link Context}, used to coordinate messaging
 * metrics between an operation and the messaging operations nested inside it.
 *
 * <p>Suppression is opt-in. Metrics are only remembered after an instrumentation calls {@link
 * #enable(Context)} on the context it starts its nested work with. Two unrelated messaging
 * instrumentations that happen to nest therefore continue emitting metrics independently.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingMetricSuppression {

  private static final ContextKey<MessagingTelemetrySignals> SUPPRESSED_MESSAGING_METRICS =
      ContextKey.named("suppressed-messaging-metrics");

  /** Returns a context that remembers suppressed metrics for operations nested inside it. */
  public static Context enable(Context context) {
    return isEnabled(context)
        ? context
        : context.with(SUPPRESSED_MESSAGING_METRICS, suppressedMetrics(context));
  }

  public static boolean isEnabled(Context context) {
    return context.get(SUPPRESSED_MESSAGING_METRICS) != null;
  }

  public static boolean isClientOperationDurationSuppressed(
      Context context, @Nullable MessagingOperationType operationType) {
    return isSuppressed(context, operationType, MessagingTelemetrySignal.CLIENT_OPERATION_DURATION);
  }

  public static Context suppressClientOperationDuration(
      Context context, @Nullable MessagingOperationType operationType) {
    return suppress(context, operationType, MessagingTelemetrySignal.CLIENT_OPERATION_DURATION);
  }

  public static boolean isProcessDurationSuppressed(Context context) {
    return isSuppressed(
        context, MessagingOperationType.PROCESS, MessagingTelemetrySignal.PROCESS_DURATION);
  }

  public static Context suppressProcessDuration(Context context) {
    return suppress(
        context, MessagingOperationType.PROCESS, MessagingTelemetrySignal.PROCESS_DURATION);
  }

  public static boolean isSentMessagesSuppressed(Context context) {
    return isSuppressed(
        context, MessagingOperationType.SEND, MessagingTelemetrySignal.SENT_MESSAGES);
  }

  public static Context suppressSentMessages(Context context) {
    return suppress(context, MessagingOperationType.SEND, MessagingTelemetrySignal.SENT_MESSAGES);
  }

  public static boolean isConsumedMessagesSuppressed(Context context) {
    return isSuppressed(
        context, MessagingOperationType.RECEIVE, MessagingTelemetrySignal.CONSUMED_MESSAGES);
  }

  public static Context suppressConsumedMessages(Context context) {
    return suppress(
        context, MessagingOperationType.RECEIVE, MessagingTelemetrySignal.CONSUMED_MESSAGES);
  }

  private static boolean isSuppressed(
      Context context,
      @Nullable MessagingOperationType operationType,
      MessagingTelemetrySignal signal) {
    return operationType != null && suppressedMetrics(context).contains(operationType, signal);
  }

  private static Context suppress(
      Context context,
      @Nullable MessagingOperationType operationType,
      MessagingTelemetrySignal signal) {
    if (!isEnabled(context) || operationType == null) {
      return context;
    }
    return context.with(
        SUPPRESSED_MESSAGING_METRICS, suppressedMetrics(context).with(operationType, signal));
  }

  private static MessagingTelemetrySignals suppressedMetrics(Context context) {
    MessagingTelemetrySignals suppressedMetrics = context.get(SUPPRESSED_MESSAGING_METRICS);
    return suppressedMetrics == null ? MessagingTelemetrySignals.none() : suppressedMetrics;
  }

  private MessagingMetricSuppression() {}
}
