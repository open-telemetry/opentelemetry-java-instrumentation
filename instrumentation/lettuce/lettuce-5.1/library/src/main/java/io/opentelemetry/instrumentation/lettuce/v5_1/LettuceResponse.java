/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import javax.annotation.Nullable;

final class LettuceResponse {

  @Nullable private final String errorMessage;
  @Nullable private final Throwable throwable;

  LettuceResponse(@Nullable String errorMessage, @Nullable Throwable throwable) {
    this.errorMessage = errorMessage;
    this.throwable = throwable;
  }

  @Nullable
  String getErrorMessage() {
    return errorMessage;
  }

  @Nullable
  Throwable getThrowable() {
    return throwable;
  }
}
