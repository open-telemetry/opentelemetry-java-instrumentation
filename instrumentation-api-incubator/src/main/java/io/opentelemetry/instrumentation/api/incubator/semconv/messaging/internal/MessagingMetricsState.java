/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

/**
 * Internal markers used to coordinate messaging metric ownership across instrumentation layers.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class MessagingMetricsState {

  private static final ContextKey<Boolean> CREATE_CLIENT_OPERATION_DURATION =
      ContextKey.named("messaging-create-client-operation-duration-metrics");
  private static final ContextKey<Boolean> SEND_CLIENT_OPERATION_DURATION =
      ContextKey.named("messaging-send-client-operation-duration-metrics");
  private static final ContextKey<Boolean> RECEIVE_CLIENT_OPERATION_DURATION =
      ContextKey.named("messaging-receive-client-operation-duration-metrics");
  private static final ContextKey<Boolean> SETTLE_CLIENT_OPERATION_DURATION =
      ContextKey.named("messaging-settle-client-operation-duration-metrics");
  private static final ContextKey<Boolean> SENT_MESSAGES =
      ContextKey.named("messaging-sent-messages-metrics");
  private static final ContextKey<Boolean> CONSUMED_MESSAGES =
      ContextKey.named("messaging-consumed-messages-metrics");
  private static final ContextKey<Boolean> PROCESS_DURATION =
      ContextKey.named("messaging-process-duration-metrics");

  public static boolean hasClientOperationDuration(
      Context context, @Nullable String operationType) {
    ContextKey<Boolean> key = clientOperationDurationKey(operationType);
    return key != null && Boolean.TRUE.equals(context.get(key));
  }

  public static Context markClientOperationDuration(
      Context context, @Nullable String operationType) {
    ContextKey<Boolean> key = clientOperationDurationKey(operationType);
    return key != null ? context.with(key, true) : context;
  }

  public static boolean hasSentMessages(Context context) {
    return Boolean.TRUE.equals(context.get(SENT_MESSAGES));
  }

  public static Context markSentMessages(Context context) {
    return context.with(SENT_MESSAGES, true);
  }

  public static boolean hasConsumedMessages(Context context) {
    return Boolean.TRUE.equals(context.get(CONSUMED_MESSAGES));
  }

  public static Context markConsumedMessages(Context context) {
    return context.with(CONSUMED_MESSAGES, true);
  }

  public static boolean hasProcessDuration(Context context) {
    return Boolean.TRUE.equals(context.get(PROCESS_DURATION));
  }

  public static Context markProcessDuration(Context context) {
    return context.with(PROCESS_DURATION, true);
  }

  @Nullable
  private static ContextKey<Boolean> clientOperationDurationKey(@Nullable String operationType) {
    if (operationType == null) {
      return null;
    }
    switch (operationType) {
      case "create":
        return CREATE_CLIENT_OPERATION_DURATION;
      case "send":
        return SEND_CLIENT_OPERATION_DURATION;
      case "receive":
        return RECEIVE_CLIENT_OPERATION_DURATION;
      case "settle":
        return SETTLE_CLIENT_OPERATION_DURATION;
      default:
        return null;
    }
  }

  private MessagingMetricsState() {}
}
