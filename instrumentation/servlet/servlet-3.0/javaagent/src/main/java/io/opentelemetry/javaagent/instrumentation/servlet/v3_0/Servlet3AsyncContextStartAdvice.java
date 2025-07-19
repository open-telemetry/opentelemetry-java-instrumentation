/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;

@SuppressWarnings("unused")
public class Servlet3AsyncContextStartAdvice {

  @AssignReturned.ToArguments(@ToArgument(0))
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Runnable start(@Advice.Argument(0) Runnable runnable) {
    return helper().wrapAsyncRunnable(runnable);
  }
}
