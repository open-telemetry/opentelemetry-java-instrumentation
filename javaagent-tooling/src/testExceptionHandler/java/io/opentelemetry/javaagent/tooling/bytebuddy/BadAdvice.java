/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class BadAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static boolean throwAnException() {
    throw new IllegalStateException("Test Exception");
  }

  public static class NoOpAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void doNothing() {
      System.currentTimeMillis();
    }
  }
}
