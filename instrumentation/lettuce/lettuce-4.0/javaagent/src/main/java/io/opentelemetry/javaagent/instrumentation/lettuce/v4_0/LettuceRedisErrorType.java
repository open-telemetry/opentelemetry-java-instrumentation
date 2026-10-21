/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisCommandExecutionException;
import com.lambdaworks.redis.output.CommandOutput;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.nio.ByteBuffer;
import java.util.Objects;
import javax.annotation.Nullable;

public final class LettuceRedisErrorType {

  private static final VirtualField<CommandOutput<?, ?, ?>, ServerError> SERVER_ERRORS =
      VirtualField.find(CommandOutput.class, ServerError.class);
  private static final ThreadLocal<ErrorDecode> ERROR_DECODE = new ThreadLocal<>();

  public static ErrorDecode startErrorDecode(@Nullable ByteBuffer bytes) {
    ErrorDecode decode = new ErrorDecode(readPrefix(bytes), ERROR_DECODE.get());
    ERROR_DECODE.set(decode);
    return decode;
  }

  public static void endErrorDecode(CommandOutput<?, ?, ?> output, @Nullable ErrorDecode decode) {
    if (decode == null) {
      return;
    }
    if (decode.previous == null) {
      ERROR_DECODE.remove();
    } else {
      ERROR_DECODE.set(decode.previous);
    }
    capture(output, decode.prefix);
  }

  public static void captureDecoded(CommandOutput<?, ?, ?> output) {
    ErrorDecode decode = ERROR_DECODE.get();
    if (decode != null) {
      capture(output, decode.prefix);
    }
  }

  @Nullable
  static String readPrefix(@Nullable ByteBuffer bytes) {
    return RedisErrorPrefix.extract(bytes);
  }

  static void capture(CommandOutput<?, ?, ?> output, @Nullable String prefix) {
    SERVER_ERRORS.set(output, prefix == null ? null : new ServerError(output.getError(), prefix));
  }

  @Nullable
  static String getErrorType(@Nullable CommandOutput<?, ?, ?> output, @Nullable Throwable error) {
    ServerError serverError = output == null ? null : SERVER_ERRORS.get(output);
    if (output != null) {
      SERVER_ERRORS.set(output, null);
    }
    if (error instanceof RedisCommandExecutionException
        && serverError != null
        && Objects.equals(serverError.message, error.getMessage())) {
      return serverError.prefix;
    }
    return getErrorType(error);
  }

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (!(error instanceof RedisCommandExecutionException)) {
      return null;
    }

    return RedisErrorPrefix.extract(error.getMessage());
  }

  private LettuceRedisErrorType() {}

  public static final class ErrorDecode {
    @Nullable private final String prefix;
    @Nullable private final ErrorDecode previous;

    private ErrorDecode(@Nullable String prefix, @Nullable ErrorDecode previous) {
      this.prefix = prefix;
      this.previous = previous;
    }
  }

  private static final class ServerError {
    @Nullable private final String message;
    private final String prefix;

    private ServerError(@Nullable String message, String prefix) {
      this.message = message;
      this.prefix = prefix;
    }
  }
}
