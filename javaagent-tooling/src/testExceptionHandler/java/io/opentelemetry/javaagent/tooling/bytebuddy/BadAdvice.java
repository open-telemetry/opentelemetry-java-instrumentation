/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;

@SuppressWarnings("unused")
public class BadAdvice {

  @AssignReturned.ToReturned
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static boolean throwAnException(@Advice.Return boolean originalReturnVal) {
    throw new IllegalStateException("Test Exception");
  }

  public static class NoOpAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void doNothing() {
      System.currentTimeMillis();
    }
  }
}
