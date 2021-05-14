/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static io.opentelemetry.javaagent.instrumentation.liberty.LibertyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterAdviceHelper;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class LibertyHandleRequestAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(value = 0) ServletRequest request,
      @Advice.Argument(value = 1) ServletResponse response) {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    // it is a bit too early to start span at this point because calling
    // some methods on HttpServletRequest will give a NPE
    // just remember the request and use it a bit later to start the span
    ThreadLocalContext.startRequest(httpServletRequest);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest servletRequest,
      @Advice.Argument(1) ServletResponse servletResponse,
      @Advice.Thrown Throwable throwable) {
    ThreadLocalContext ctx = ThreadLocalContext.endRequest();
    if (ctx == null) {
      return;
    }

    Context context = ctx.getContext();
    Scope scope = ctx.getScope();
    if (scope == null) {
      return;
    }
    scope.close();

    if (context == null) {
      // an existing span was found
      return;
    }

    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    tracer().setPrincipal(context, request);

    if (throwable != null) {
      tracer().endExceptionally(context, throwable, response);
      return;
    }

    if (ServletAndFilterAdviceHelper.mustEndOnHandlerMethodExit(tracer(), request)) {
      tracer().end(context, response);
    }
  }
}
