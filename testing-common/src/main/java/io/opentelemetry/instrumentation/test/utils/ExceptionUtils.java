/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import static java.util.Objects.requireNonNull;

public class ExceptionUtils {

  static RuntimeException sneakyThrow(Throwable t) {
    requireNonNull(t, "t");
    return ExceptionUtils.sneakyThrow0(t);
  }

  // Exactly what we want
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  private static <T extends Throwable> T sneakyThrow0(Throwable t) throws T {
    throw (T) t;
  }

  private ExceptionUtils() {}
}
