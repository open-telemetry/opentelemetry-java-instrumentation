/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.netty.buffer.ByteBuf;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.redisson.client.RedisException;

/**
 * Captures the first token from RESP error replies before Redisson completes the command promise.
 *
 * <p>Redisson's {@code CommandDecoder} calls {@code CommandData.tryFailure} while decoding a {@code
 * -} reply, so the token is scoped to that decoder frame and attached to the resulting Redisson
 * exception before completion callbacks can run. The operation listener clears it after extracting
 * telemetry; all other errors use the shared exception-class fallback.
 *
 * @see <a
 *     href="https://redis.io/docs/latest/develop/reference/protocol-spec/#resp-protocol-description">RESP
 *     specification</a>
 * @see <a
 *     href="https://github.com/redisson/redisson/blob/3.52.0/redisson/src/main/java/org/redisson/client/handler/CommandDecoder.java">Redisson
 *     CommandDecoder</a>
 */
public final class RedissonErrorCapture {

  private static final ScopedThreadValue<String> currentServerErrorType = new ScopedThreadValue<>();

  private static final VirtualField<RedisException, RedisServerError> SERVER_ERROR =
      VirtualField.find(RedisException.class, RedisServerError.class);

  public static ScopedThreadValue<String> currentServerErrorType() {
    return currentServerErrorType;
  }

  @Nullable
  public static String parseServerErrorType(ByteBuf input) {
    try {
      int readerIndex = input.readerIndex();
      if (input.getByte(readerIndex) != '-') {
        return null;
      }

      int start = readerIndex + 1;
      int writerIndex = input.writerIndex();
      int replyEnd = start;
      while (replyEnd + 1 < writerIndex
          && (input.getByte(replyEnd) != '\r' || input.getByte(replyEnd + 1) != '\n')) {
        replyEnd++;
      }
      if (replyEnd + 1 >= writerIndex) {
        return null;
      }

      return RedisErrorPrefix.extract(input.nioBuffer(start, replyEnd - start));
    } catch (Throwable ignored) {
      // ReplayingDecoder retries incomplete replies; leave buffer handling to Redisson.
      return null;
    }
  }

  public static void captureServerError(@Nullable Throwable error) {
    if (!(error instanceof RedisException)) {
      return;
    }

    String errorType = currentServerErrorType.get();
    if (errorType != null) {
      SERVER_ERROR.set((RedisException) error, new RedisServerError(errorType));
    }
  }

  @Nullable
  public static String getServerErrorType(@Nullable Throwable error) {
    if (!(error instanceof RedisException)) {
      return null;
    }

    RedisServerError redisServerError = SERVER_ERROR.get((RedisException) error);
    return redisServerError != null ? redisServerError.errorType : null;
  }

  public static void clearServerError(@Nullable Throwable error) {
    if (error instanceof RedisException) {
      SERVER_ERROR.set((RedisException) error, null);
    }
  }

  private static final class RedisServerError {
    private final String errorType;

    private RedisServerError(String errorType) {
      this.errorType = errorType;
    }
  }

  private RedissonErrorCapture() {}
}
