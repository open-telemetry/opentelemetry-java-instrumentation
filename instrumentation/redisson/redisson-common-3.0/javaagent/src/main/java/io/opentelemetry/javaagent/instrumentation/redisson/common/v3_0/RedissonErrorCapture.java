/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static java.nio.charset.StandardCharsets.UTF_8;

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

  private static final VirtualField<RedisException, String> SERVER_ERROR =
      VirtualField.find(RedisException.class, String.class, "redissonServerErrorType");

  public static ScopedThreadValue<String> currentServerErrorType() {
    return currentServerErrorType;
  }

  @Nullable
  public static String parseServerErrorType(ByteBuf input) {
    try {
      int replyStart = input.readerIndex();
      if (input.getByte(replyStart) != '-') {
        return null;
      }

      int lineStart = replyStart + 1;
      int lineEnd = findCrlf(input, lineStart);
      if (lineEnd == -1) {
        return null;
      }

      return RedisErrorPrefix.extract(input.toString(lineStart, lineEnd - lineStart, UTF_8));
    } catch (Throwable ignored) {
      // ReplayingDecoder retries incomplete replies; leave buffer handling to Redisson.
      return null;
    }
  }

  private static int findCrlf(ByteBuf input, int start) {
    int end = input.writerIndex();
    for (int i = start; i + 1 < end; i++) {
      if (input.getByte(i) == '\r' && input.getByte(i + 1) == '\n') {
        return i;
      }
    }
    return -1;
  }

  public static void captureServerError(@Nullable Throwable error) {
    if (!(error instanceof RedisException)) {
      return;
    }

    String errorType = currentServerErrorType.get();
    if (errorType != null) {
      SERVER_ERROR.set((RedisException) error, errorType);
      // consume so completion callbacks run by tryFailure cannot attribute it to another command
      currentServerErrorType.set(null);
    }
  }

  @Nullable
  public static String getServerErrorType(@Nullable Throwable error) {
    if (!(error instanceof RedisException)) {
      return null;
    }

    return SERVER_ERROR.get((RedisException) error);
  }

  public static void clearServerError(@Nullable Throwable error) {
    if (error instanceof RedisException) {
      SERVER_ERROR.set((RedisException) error, null);
    }
  }

  private RedissonErrorCapture() {}
}
