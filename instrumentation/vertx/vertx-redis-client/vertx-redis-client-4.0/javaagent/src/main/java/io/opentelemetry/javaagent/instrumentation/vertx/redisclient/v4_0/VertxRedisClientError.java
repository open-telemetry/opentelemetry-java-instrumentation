/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.redis.client.impl.types.ErrorType;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

public final class VertxRedisClientError {
  private static final VirtualField<ErrorType, VertxRedisClientError> SERVER_ERROR =
      VirtualField.find(ErrorType.class, VertxRedisClientError.class);
  private static final VirtualField<Promise<?>, BatchErrors> BATCH_ERRORS =
      VirtualField.find(Promise.class, BatchErrors.class);

  @Nullable private final String errorType;

  private VertxRedisClientError(@Nullable String errorType) {
    this.errorType = errorType;
  }

  public static void captureServerError(ErrorType error) {
    String reply = error.getMessage();
    if (reply != null) {
      // ReadableBuffer.readLine maps wire bytes to ISO-8859-1, preserving them for UTF-8 decoding.
      reply = new String(reply.getBytes(ISO_8859_1), UTF_8);
    }
    SERVER_ERROR.set(error, new VertxRedisClientError(parseErrorType(reply)));
  }

  @Nullable
  static VertxRedisClientError get(@Nullable Throwable error) {
    return error instanceof ErrorType ? SERVER_ERROR.get((ErrorType) error) : null;
  }

  @Nullable
  String getErrorType() {
    return errorType;
  }

  // visible for testing
  @Nullable
  static String parseErrorType(@Nullable String reply) {
    if (reply == null) {
      return null;
    }
    int start = 0;
    while (start < reply.length() && reply.charAt(start) == ' ') {
      start++;
    }
    if (start == reply.length()) {
      return null;
    }
    int separator = reply.indexOf(' ', start);
    return separator == -1 ? reply.substring(start) : reply.substring(start, separator);
  }

  @Nullable
  public static BatchCompletion recordBatchResult(
      Promise<?> promise, int index, AsyncResult<?> result, AtomicInteger remaining) {
    if (promise.future().isComplete()) {
      return null;
    }
    BatchErrors errors = BATCH_ERRORS.get(promise);
    if (errors == null) {
      errors = new BatchErrors();
      BATCH_ERRORS.set(promise, errors);
      promise.future().onComplete(unused -> BATCH_ERRORS.set(promise, null));
    }
    if (result.failed()) {
      errors.record(index, result.cause());
    }
    return new BatchCompletion(errors, remaining);
  }

  public static void captureBatchError(ErrorType error, @Nullable BatchCompletion completion) {
    // The batch callback constructs its aggregate only after decrementing the reply count to zero.
    if (completion != null && completion.remaining.get() == 0 && !completion.captured) {
      completion.captured = true;
      BatchErrors errors = completion.errors;
      if (!errors.clientFailure) {
        SERVER_ERROR.set(error, errors.firstError);
      }
    }
  }

  public static final class BatchErrors {
    private int firstIndex = Integer.MAX_VALUE;
    @Nullable private VertxRedisClientError firstError;
    private boolean clientFailure;

    private void record(int index, @Nullable Throwable error) {
      VertxRedisClientError serverError = get(error);
      if (serverError == null) {
        clientFailure = true;
      }
      if (index < firstIndex) {
        firstIndex = index;
        firstError = serverError;
      }
    }
  }

  public static final class BatchCompletion {
    private final BatchErrors errors;
    private final AtomicInteger remaining;
    private boolean captured;

    private BatchCompletion(BatchErrors errors, AtomicInteger remaining) {
      this.errors = errors;
      this.remaining = remaining;
    }
  }
}
