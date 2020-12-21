/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class LibertyHttpServerTracer extends Servlet3HttpServerTracer {
  private static final LibertyHttpServerTracer TRACER = new LibertyHttpServerTracer();

  public static LibertyHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  public Context startSpan(HttpServletRequest request) {
    // using request method as span name as server isn't ready for calling request.getServletPath()
    // span name will be updated a bit later when calling request.getServletPath() works
    // see LibertyUpdateSpanAdvice
    Context context = startSpan(request, request, request, "HTTP " + request.getMethod());
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
      context = context.with(ServletContextPath.CONTEXT_KEY, contextPath);
    }
    return context;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.liberty";
  }
}
