/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.List;
import javax.annotation.Nullable;

/** Observes raw replies without building or consuming application-owned Responses. */
public final class JedisBatchResponse {
  private static final ContextKey<JedisBatchResponse> KEY =
      ContextKey.named("opentelemetry-jedis-batch-response");

  @Nullable private final Object connection;
  private final int commandCount;
  @Nullable private Throwable failure;

  private JedisBatchResponse(@Nullable Object connection, int commandCount) {
    this.connection = connection;
    this.commandCount = commandCount;
  }

  public static Context store(Context context, @Nullable Object connection, int commandCount) {
    return context.with(KEY, new JedisBatchResponse(connection, commandCount));
  }

  public static void capture(Object connection, @Nullable List<?> replies) {
    JedisBatchResponse batch = Context.current().get(KEY);
    if (batch == null
        || batch.connection != connection
        || replies == null
        || replies.size() != batch.commandCount
        || replies.isEmpty()) {
      return;
    }
    // getMany/getAll also drain transaction framing replies. Only a complete set of command
    // errors establishes an all-failed aggregate; successful, empty and aborted replies do not.
    for (Object reply : replies) {
      if (!(reply instanceof Throwable)) {
        return;
      }
    }
    batch.failure = (Throwable) replies.get(0);
  }

  @Nullable
  public static Throwable getError(@Nullable Context context, @Nullable Throwable error) {
    // A terminal failure (including connection failures after reading replies) takes precedence.
    if (error != null) {
      return error;
    }
    JedisBatchResponse batch = context != null ? context.get(KEY) : null;
    return batch != null ? batch.failure : null;
  }

  @Nullable
  public static String getErrorType(@Nullable String message) {
    if (message == null || message.isEmpty()) {
      return null;
    }
    int end = message.indexOf(' ');
    if (end == -1) {
      end = message.length();
    }
    // Redis protocol errors start with an uppercase error code, not the full server message.
    for (int i = 0; i < end; i++) {
      char c = message.charAt(i);
      if (!(c >= 'A' && c <= 'Z') && c != '_') {
        return null;
      }
    }
    return end == 0 ? null : message.substring(0, end);
  }
}
