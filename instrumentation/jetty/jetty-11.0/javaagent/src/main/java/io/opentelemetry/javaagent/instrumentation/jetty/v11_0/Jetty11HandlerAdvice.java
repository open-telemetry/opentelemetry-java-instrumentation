/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.v11_0.Jetty11HttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHandlerAdviceHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Jetty11HandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This Object source,
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    Context attachedContext = tracer().getServerContext(request);
    if (attachedContext != null) {
      // We are inside nested handler, don't create new span
      return;
    }

    context = tracer().startServerSpan(request);
    scope = context.makeCurrent();

    // Must be set here since Jetty handlers can use startAsync outside of servlet scope.
    tracer().setAsyncListenerResponse(request, response);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    JettyHandlerAdviceHelper.stopSpan(tracer(), request, response, throwable, context, scope);
  }
}
