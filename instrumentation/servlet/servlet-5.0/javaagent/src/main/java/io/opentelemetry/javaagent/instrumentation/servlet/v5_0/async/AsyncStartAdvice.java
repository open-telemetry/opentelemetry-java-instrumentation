/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.async;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.helper;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class AsyncStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static CallDepth startAsyncEnter() {
    // This allows to detect the outermost invocation of startAsync in method exit
    CallDepth callDepth = CallDepth.forClass(AsyncContext.class);
    callDepth.getAndIncrement();
    return callDepth;
  }

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void startAsyncExit(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) HttpServletRequest request,
      @Advice.Enter CallDepth callDepth) {

    if (callDepth.decrementAndGet() != 0) {
      // This is not the outermost invocation, ignore.
      return;
    }

    if (request != null) {
      helper().attachAsyncListener(request, Java8BytecodeBridge.currentContext());
    }
  }
}
