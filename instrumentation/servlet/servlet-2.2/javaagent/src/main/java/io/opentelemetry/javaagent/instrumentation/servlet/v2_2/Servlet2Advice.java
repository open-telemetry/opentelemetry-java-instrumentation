/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class Servlet2Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) ServletResponse response,
      @Advice.Local("otelCallDepth") CallDepth callDepth,
      @Advice.Local("otelRequest") ServletRequestContext<HttpServletRequest> requestContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    callDepth = CallDepth.forClass(AppServerBridge.getCallDepthKey());
    callDepth.getAndIncrement();

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    Context serverContext = helper().getServerContext(httpServletRequest);
    if (serverContext != null) {
      Context updatedContext = helper().updateContext(serverContext, httpServletRequest);
      if (updatedContext != serverContext) {
        // updateContext updated context, need to re-scope
        scope = updatedContext.makeCurrent();
      }
      return;
    }

    Context parentContext = Java8BytecodeBridge.currentContext();
    requestContext = new ServletRequestContext<>(httpServletRequest);

    if (!helper().shouldStart(parentContext, requestContext)) {
      return;
    }

    context = helper().start(parentContext, requestContext);
    scope = context.makeCurrent();
    // reset response status from previous request
    // (some servlet containers reuse response objects to reduce memory allocations)
    VirtualField.find(ServletResponse.class, Integer.class).set(response, null);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelCallDepth") CallDepth callDepth,
      @Advice.Local("otelRequest") ServletRequestContext<HttpServletRequest> requestContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    int depth = callDepth.decrementAndGet();

    if (scope != null) {
      scope.close();
    }

    if (context == null && depth == 0) {
      Context currentContext = Java8BytecodeBridge.currentContext();
      // Something else is managing the context, we're in the outermost level of Servlet
      // instrumentation and we have an uncaught throwable. Let's add it to the current span.
      if (throwable != null) {
        helper().recordException(currentContext, throwable);
      }
    }

    if (scope == null || context == null) {
      return;
    }

    int responseStatusCode = HttpServletResponse.SC_OK;
    Integer responseStatus = VirtualField.find(ServletResponse.class, Integer.class).get(response);
    if (responseStatus != null) {
      responseStatusCode = responseStatus;
    }

    helper()
        .stopSpan(
            context, requestContext, (HttpServletResponse) response, responseStatusCode, throwable);
  }
}
