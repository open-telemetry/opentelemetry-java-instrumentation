/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.async;

import static io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletHttpServerTracer.tracer;

import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class AsyncStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void startAsyncEnter() {
    // This allows to detect the outermost invocation of startAsync in method exit
    CallDepth.forClass(AsyncContext.class).getAndIncrement();
  }

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void startAsyncExit(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) HttpServletRequest request) {

    int callDepth = CallDepth.forClass(AsyncContext.class).decrementAndGet();

    if (callDepth != 0) {
      // This is not the outermost invocation, ignore.
      return;
    }

    if (request != null) {
      if (!tracer().isAsyncListenerAttached(request)) {
        tracer().attachAsyncListener(request);
      }
    }
  }
}
