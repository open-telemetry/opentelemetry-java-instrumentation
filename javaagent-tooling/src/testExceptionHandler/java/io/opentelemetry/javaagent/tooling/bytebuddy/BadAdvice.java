/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class BadAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void throwAnException(@Advice.Return AtomicBoolean isInstrumented) {
    // mark that the advice has been executed
    isInstrumented.set(true);
    throw new IllegalStateException("Test Exception");
  }

  public static class NoOpAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void doNothing() {
      System.currentTimeMillis();
    }
  }
}
