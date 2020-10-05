/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test;

import net.bytebuddy.asm.Advice;

public class BadAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void throwAnException(@Advice.Return(readOnly = false) boolean returnVal) {
    returnVal = true;
    throw new RuntimeException("Test Exception");
  }

  public static class NoOpAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void doNothing() {
      System.currentTimeMillis();
    }
  }
}
