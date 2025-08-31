/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet3AsyncStartAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static CallDepth startAsyncEnter() {
    CallDepth callDepth = CallDepth.forClass(AsyncContext.class);
    // This allows to detect the outermost invocation of startAsync in method exit
    callDepth.getAndIncrement();
    return callDepth;
  }

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void startAsyncExit(
      @Advice.This ServletRequest servletRequest, @Advice.Enter CallDepth callDepth) {

    if (callDepth.decrementAndGet() != 0) {
      // This is not the outermost invocation, ignore.
      return;
    }

    if (servletRequest instanceof HttpServletRequest) {
      HttpServletRequest request = (HttpServletRequest) servletRequest;

      helper().attachAsyncListener(request, Java8BytecodeBridge.currentContext());
    }
  }
}
