/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

public class ExceptionUtils {

  static RuntimeException sneakyThrow(Throwable t) {
    if (t == null) throw new NullPointerException("t");
    return ExceptionUtils.<RuntimeException>sneakyThrow0(t);
  }

  private static <T extends Throwable> T sneakyThrow0(Throwable t) throws T {
    throw (T) t;
  }
}
