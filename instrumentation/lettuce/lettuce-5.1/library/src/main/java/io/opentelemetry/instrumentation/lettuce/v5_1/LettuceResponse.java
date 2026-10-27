/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.output.CommandOutput;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;

final class LettuceResponse {

  private final boolean failed;
  @Nullable private final String redisErrorCode;

  static LettuceResponse fromErrorMessage(@Nullable String errorMessage) {
    return new LettuceResponse(errorMessage != null, RedisErrorPrefix.extract(errorMessage));
  }

  static LettuceResponse fromCommandOutput(@Nullable CommandOutput<?, ?, ?> output) {
    return fromErrorMessage(output != null ? output.getError() : null);
  }

  static LettuceResponse unknownFailure() {
    return new LettuceResponse(true, null);
  }

  private LettuceResponse(boolean failed, @Nullable String redisErrorCode) {
    this.failed = failed;
    this.redisErrorCode = redisErrorCode;
  }

  boolean isFailed() {
    return failed;
  }

  @Nullable
  String getRedisErrorCode() {
    return redisErrorCode;
  }
}
