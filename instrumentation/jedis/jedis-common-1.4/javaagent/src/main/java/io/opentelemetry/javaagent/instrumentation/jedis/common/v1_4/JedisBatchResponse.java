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
  private boolean failed;
  @Nullable private String errorType;

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
    batch.failed = true;
    batch.errorType = JedisRedisErrorType.getErrorType((Throwable) replies.get(0));
  }

  @Nullable
  public static JedisBatchResponse getResponse(Context context) {
    return context.get(KEY);
  }

  public boolean isFailed() {
    return failed;
  }

  @Nullable
  public String getErrorType() {
    return errorType;
  }
}
