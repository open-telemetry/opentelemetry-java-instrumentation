/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2HttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class Servlet2Advice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) ServletResponse response,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Servlet2Advice.class);

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    Context serverContext = tracer().getServerContext(httpServletRequest);
    if (serverContext != null) {
      if (!AppServerBridge.isBetterNameSuggested(serverContext)) {
        tracer().updateServerSpanName(httpServletRequest);
        AppServerBridge.setBetterNameSuggested(serverContext, true);
      }
      return;
    }

    context = tracer().startSpan(httpServletRequest);
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (CallDepthThreadLocalMap.decrementCallDepth(Servlet2Advice.class) == 0
        && throwable != null) {
      AppServerBridge.setThrowableToContext(throwable, Context.current());
    }

    if (scope == null) {
      return;
    }
    scope.close();

    tracer().setPrincipal(context, (HttpServletRequest) request);

    Integer responseStatus =
        InstrumentationContext.get(ServletResponse.class, Integer.class).get(response);

    ResponseWithStatus responseWithStatus =
        new ResponseWithStatus((HttpServletResponse) response, responseStatus);
    if (throwable == null) {
      tracer().end(context, responseWithStatus);
    } else {
      tracer().endExceptionally(context, throwable, responseWithStatus);
    }
  }
}
