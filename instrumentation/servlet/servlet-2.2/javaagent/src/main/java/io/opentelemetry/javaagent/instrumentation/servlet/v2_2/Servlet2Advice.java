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
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
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
    CallDepthThreadLocalMap.incrementCallDepth(AppServerBridge.getCallDepthKey());

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    Context serverContext = tracer().getServerContext(httpServletRequest);
    if (serverContext != null) {
      Context updatedContext = tracer().updateContext(serverContext, httpServletRequest);
      if (updatedContext != serverContext) {
        // updateContext updated context, need to re-scope
        scope = updatedContext.makeCurrent();
      }
      return;
    }

    context = tracer().startSpan(httpServletRequest);
    scope = context.makeCurrent();
    // reset response status from previous request
    // (some servlet containers reuse response objects to reduce memory allocations)
    InstrumentationContext.get(ServletResponse.class, Integer.class).put(response, null);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    int callDepth = CallDepthThreadLocalMap.decrementCallDepth(AppServerBridge.getCallDepthKey());

    if (scope != null) {
      scope.close();
    }

    if (context == null && callDepth == 0) {
      Context currentContext = Java8BytecodeBridge.currentContext();
      // Something else is managing the context, we're in the outermost level of Servlet
      // instrumentation and we have an uncaught throwable. Let's add it to the current span.
      if (throwable != null) {
        tracer().addUnwrappedThrowable(currentContext, throwable);
      }
      tracer().setPrincipal(currentContext, (HttpServletRequest) request);
    }

    if (scope == null || context == null) {
      return;
    }

    tracer().setPrincipal(context, (HttpServletRequest) request);

    int responseStatusCode = HttpServletResponse.SC_OK;
    Integer responseStatus =
        InstrumentationContext.get(ServletResponse.class, Integer.class).get(response);
    if (responseStatus != null) {
      responseStatusCode = responseStatus;
    }

    ResponseWithStatus responseWithStatus =
        new ResponseWithStatus((HttpServletResponse) response, responseStatusCode);
    if (throwable == null) {
      tracer().end(context, responseWithStatus);
    } else {
      tracer().endExceptionally(context, throwable, responseWithStatus);
    }
  }
}
