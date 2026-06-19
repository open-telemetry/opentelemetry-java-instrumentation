/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1.internal;

import javax.annotation.Nullable;

/**
 * Lettuce response data captured for telemetry.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LettuceResponse {

  @Nullable private final String errorMessage;
  @Nullable private final Throwable throwable;

  LettuceResponse(@Nullable String errorMessage, @Nullable Throwable throwable) {
    this.errorMessage = errorMessage;
    this.throwable = throwable;
  }

  @Nullable
  public String getErrorMessage() {
    return errorMessage;
  }

  @Nullable
  Throwable getThrowable() {
    return throwable;
  }
}
