/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2HttpServerTracer.TRACER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
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
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    if (TRACER.getServerContext(httpServletRequest) != null) {
      return;
    }

    span = TRACER.startSpan(httpServletRequest);
    scope = TRACER.startScope(span, httpServletRequest);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    TRACER.setPrincipal(span, (HttpServletRequest) request);

    Integer responseStatus =
        InstrumentationContext.get(ServletResponse.class, Integer.class).get(response);

    ResponseWithStatus responseWithStatus =
        new ResponseWithStatus((HttpServletResponse) response, responseStatus);
    if (throwable == null) {
      TRACER.end(span, responseWithStatus);
    } else {
      TRACER.endExceptionally(span, throwable, responseWithStatus);
    }
  }
}
