/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import javax.annotation.Nullable;

/**
 * Internal utilities for normalizing database error types.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DbErrorTypeUtil {

  /**
   * Returns the decimal representation of a database vendor error code, or {@code null} when the
   * code is zero and therefore unavailable.
   */
  @Nullable
  public static String fromErrorCode(int errorCode) {
    return errorCode == 0 ? null : Integer.toString(errorCode);
  }

  private DbErrorTypeUtil() {}
}
