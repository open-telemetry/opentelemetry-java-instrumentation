/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3AsyncContextStartAdvice {

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
