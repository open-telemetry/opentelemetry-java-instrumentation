/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3AsyncContextStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(@Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
    runnable = helper().wrapAsyncRunnable(runnable);
  }
}
