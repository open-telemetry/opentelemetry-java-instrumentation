/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax.dispatcher;

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.common.dispatcher.RequestDispatcherAdviceHelper;
import java.lang.reflect.Method;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;

public class RequestDispatcherAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(
      @Advice.Origin Method method,
      @Advice.Local("otelRequestContext") Context requestContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Argument(0) ServletRequest request) {

    Context currentContext = Java8BytecodeBridge.currentContext();

    Object requestContextAttr = request.getAttribute(CONTEXT_ATTRIBUTE);
    requestContext = requestContextAttr instanceof Context ? (Context) requestContextAttr : null;

    context = RequestDispatcherAdviceHelper.getStartParentContext(currentContext, requestContext);
    if (context == null) {
      return;
    }

    // this tells the dispatched servlet to use the current span as the parent for its work
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stop(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Local("otelRequestContext") Context requestContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Thrown Throwable throwable) {

    if (requestContext != null) {
      // restore the original request context
      request.setAttribute(CONTEXT_ATTRIBUTE, requestContext);
    }
  }
}
