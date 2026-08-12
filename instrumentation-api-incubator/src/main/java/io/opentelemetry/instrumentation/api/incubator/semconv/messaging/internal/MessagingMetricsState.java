/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Internal markers used to coordinate messaging metric ownership across instrumentation layers.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class MessagingMetricsState {

  private static final ContextKey<Boolean> CONSUMED_MESSAGES =
      ContextKey.named("messaging-consumed-messages-metrics");
  private static final ContextKey<Boolean> PROCESS_DURATION =
      ContextKey.named("messaging-process-duration-metrics");

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

  private MessagingMetricsState() {}
}
