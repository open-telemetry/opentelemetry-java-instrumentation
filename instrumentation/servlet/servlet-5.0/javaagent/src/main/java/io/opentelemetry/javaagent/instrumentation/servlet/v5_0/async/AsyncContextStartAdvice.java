/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.async;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.helper;

import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class AsyncContextStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(@Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
    runnable = helper().wrapAsyncRunnable(runnable);
  }
}
