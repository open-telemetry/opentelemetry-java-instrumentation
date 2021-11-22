/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.async;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.helper;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class AsyncContextStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(
      @Advice.This AsyncContext asyncContext,
      @Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
    ServletRequest request = asyncContext.getRequest();
    if (request instanceof HttpServletRequest) {
      runnable = helper().wrapAsyncRunnable((HttpServletRequest) request, runnable);
    }
  }
}
