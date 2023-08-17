/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import net.bytebuddy.asm.Advice;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class PatchTestAdvice {

  public static final AtomicInteger invocationCount = new AtomicInteger(0);

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onExit() {
    invocationCount.incrementAndGet();
  }

}
