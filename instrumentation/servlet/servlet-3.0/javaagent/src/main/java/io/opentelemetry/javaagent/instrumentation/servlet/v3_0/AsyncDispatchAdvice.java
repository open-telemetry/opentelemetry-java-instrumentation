/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper.CONTEXT_ATTRIBUTE;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class AsyncDispatchAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void enter(
      @Advice.This AsyncContext context,
      @Advice.AllArguments Object[] args,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {
    callDepth = CallDepth.forClass(AsyncContext.class);
    if (callDepth.getAndIncrement() > 0) {
      return;
    }

    ServletRequest request = context.getRequest();

    Context currentContext = Java8BytecodeBridge.currentContext();
    Span currentSpan = Java8BytecodeBridge.spanFromContext(currentContext);
    if (currentSpan.getSpanContext().isValid()) {
      // this tells the dispatched servlet to use the current span as the parent for its work
      // (if the currentSpan is not valid for some reason, the original servlet span should still
      // be present in the same request attribute, and so that will be used)
      //
      // the original servlet span stored in the same request attribute does not need to be saved
      // and restored on method exit, because dispatch() hands off control of the request
      // processing, and nothing can be done with the request anymore after this
      request.setAttribute(CONTEXT_ATTRIBUTE, currentContext);
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
    callDepth.decrementAndGet();
  }
}
