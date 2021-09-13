/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3AsyncStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void startAsyncEnter(@Advice.Local("otelCallDepth") CallDepth callDepth) {
    // This allows to detect the outermost invocation of startAsync in method exit
    callDepth = CallDepth.forClass(AsyncContext.class);
    callDepth.getAndIncrement();
  }

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void startAsyncExit(
      @Advice.This ServletRequest servletRequest,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {

    if (callDepth.decrementAndGet() != 0) {
      // This is not the outermost invocation, ignore.
      return;
    }

    if (servletRequest instanceof HttpServletRequest) {
      HttpServletRequest request = (HttpServletRequest) servletRequest;

      if (!helper().isAsyncListenerAttached(request)) {
        helper().attachAsyncListener(request);
      }
    }
  }
}
