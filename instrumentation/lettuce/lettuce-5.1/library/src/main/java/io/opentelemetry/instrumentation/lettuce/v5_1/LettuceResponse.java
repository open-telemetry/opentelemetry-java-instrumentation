/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.output.MultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Nullable;

final class LettuceResponse {

  private static final boolean LEGACY_ERROR_DECODING = usesLegacyErrorDecoding();
  private static final boolean LEGACY_TRANSACTION_ERROR_DECODING =
      usesLegacyTransactionErrorDecoding();

  private final boolean failed;
  @Nullable private final String redisErrorType;

  static LettuceResponse fromErrorMessage(@Nullable String errorMessage) {
    return new LettuceResponse(errorMessage != null, extractRedisErrorPrefix(errorMessage));
  }

  static LettuceResponse fromCommandOutput(
      @Nullable CommandOutput<?, ?, ?> output, boolean transactional) {
    return fromErrorMessage(
        decodeErrorMessage(
            output != null ? output.getError() : null,
            transactional ? LEGACY_TRANSACTION_ERROR_DECODING : LEGACY_ERROR_DECODING));
  }

  static LettuceResponse fromTransactionErrorMessage(@Nullable String errorMessage) {
    return fromErrorMessage(decodeErrorMessage(errorMessage, LEGACY_TRANSACTION_ERROR_DECODING));
  }

  static LettuceResponse unknownFailure() {
    return new LettuceResponse(true, null);
  }

  private LettuceResponse(boolean failed, @Nullable String redisErrorType) {
    this.failed = failed;
    this.redisErrorType = redisErrorType;
  }

  boolean isFailed() {
    return failed;
  }

  @Nullable
  String getRedisErrorType() {
    return redisErrorType;
  }

  private static boolean usesLegacyErrorDecoding() {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    output.setError(ByteBuffer.wrap("é".getBytes(UTF_8)));
    return "ￃﾩ".equals(output.getError());
  }

  private static boolean usesLegacyTransactionErrorDecoding() {
    StatusOutput<String, String> output = new StatusOutput<>(StringCodec.UTF8);
    MultiOutput<String, String> multi = new MultiOutput<>(StringCodec.UTF8);
    multi.add(new Command<>(CommandType.EVAL, output));
    multi.multi(1);
    multi.setError(ByteBuffer.wrap("é".getBytes(UTF_8)));
    return "ￃﾩ".equals(output.getError());
  }

  @Nullable
  private static String decodeErrorMessage(
      @Nullable String errorMessage, boolean legacyErrorDecoding) {
    if (!legacyErrorDecoding || errorMessage == null) {
      return errorMessage;
    }
    // The legacy decoder casts each signed byte to char. Reverse that mapping only for output
    // from that decoder, leaving application strings and UTF-8-decoding versions untouched.
    boolean hasEncodedBytes = false;
    for (int i = 0; i < errorMessage.length(); i++) {
      char c = errorMessage.charAt(i);
      if (c > 0x7f && c < 0xff80) {
        return errorMessage;
      }
      hasEncodedBytes |= c >= 0xff80;
    }
    if (!hasEncodedBytes) {
      return errorMessage;
    }
    byte[] bytes = new byte[errorMessage.length()];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) errorMessage.charAt(i);
    }
    String decoded = new String(bytes, UTF_8);
    return Arrays.equals(bytes, decoded.getBytes(UTF_8)) ? decoded : errorMessage;
  }

  @Nullable
  private static String extractRedisErrorPrefix(@Nullable String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      return null;
    }

    int start = 0;
    while (start < errorMessage.length() && errorMessage.charAt(start) == ' ') {
      start++;
    }
    if (start == errorMessage.length()) {
      return null;
    }
    int separator = errorMessage.indexOf(' ', start);
    return separator == -1
        ? errorMessage.substring(start)
        : errorMessage.substring(start, separator);
  }
}
