/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.async;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.helper;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class AsyncStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void startAsyncEnter(@Advice.Local("otelCallDepth") CallDepth callDepth) {
    // This allows to detect the outermost invocation of startAsync in method exit
    callDepth = CallDepth.forClass(AsyncContext.class);
    callDepth.getAndIncrement();
  }

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void startAsyncExit(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) HttpServletRequest request,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {

    if (callDepth.decrementAndGet() != 0) {
      // This is not the outermost invocation, ignore.
      return;
    }

    if (request != null) {
      if (!helper().isAsyncListenerAttached(request)) {
        helper().attachAsyncListener(request);
      }
    }
  }
}
