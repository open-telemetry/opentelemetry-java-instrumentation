/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

public final class ExceptionUtils {

  @SuppressWarnings("unchecked")
  static RuntimeException sneakyThrow(Throwable t) {
    if (t == null) {
      throw new NullPointerException("t");
    }
    return ExceptionUtils.sneakyThrow0(t);
  }

  // Exactly what we want
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  private static <T extends Throwable> T sneakyThrow0(Throwable t) throws T {
    throw (T) t;
  }

  private ExceptionUtils() {}
}
