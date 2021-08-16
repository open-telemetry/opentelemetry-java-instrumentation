/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jetty.v8_0.Jetty8Singletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Jetty8HandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This Object source,
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    //    Context attachedContext = tracer().getServerContext(request);
    //    if (attachedContext != null) {
    //      // We are inside nested handler, don't create new span
    //      return;
    //    }
    //
    //    context = tracer().startServerSpan(request);
    //    scope = context.makeCurrent();

    // Must be set here since Jetty handlers can use startAsync outside of servlet scope.

    //    tracer().setAsyncListenerResponse(request, response);

    Context parentContext = currentContext();
    if (instrumenter().shouldStart(parentContext, request)) {
      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    if (scope == null) {
      return;
    }
    scope.close();

    if (throwable != null) {
      if(response.getStatus() < 400){
        response.setStatus(500);
      }
    }

    instrumenter().end(context, request, response, throwable);
  }
}
