/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.List;
import javax.annotation.Nullable;

/** Observes raw replies without building or consuming application-owned Responses. */
public final class JedisBatchState {
  private static final ContextKey<JedisBatchState> KEY =
      ContextKey.named("opentelemetry-jedis-batch-state");

  @Nullable private final Object connection;
  private final int commandCount;
  @Nullable private String errorType;

  private JedisBatchState(@Nullable Object connection, int commandCount) {
    this.connection = connection;
    this.commandCount = commandCount;
  }

  public static Context store(Context context, @Nullable Object connection, int commandCount) {
    return context.with(KEY, new JedisBatchState(connection, commandCount));
  }

  public static void capture(Object connection, @Nullable List<?> replies) {
    JedisBatchState batch = Context.current().get(KEY);
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
    batch.errorType = JedisRedisErrorType.getErrorType((Throwable) replies.get(0));
  }

  @Nullable
  public static String getErrorType(Context context, @Nullable Throwable throwable) {
    if (throwable != null) {
      return JedisRedisErrorType.getErrorType(throwable);
    }
    JedisBatchState batch = context.get(KEY);
    return batch != null ? batch.errorType : null;
  }
}
