/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class JettyHttpServerTracer extends Servlet3HttpServerTracer {
  private static final JettyHttpServerTracer TRACER = new JettyHttpServerTracer();

  public static JettyHttpServerTracer tracer() {
    return TRACER;
  }

  public Context startServerSpan(HttpServletRequest request) {
    return startSpan(request, "HTTP " + request.getMethod());
  }

  @Override
  protected Context customizeContext(Context context, HttpServletRequest request) {
    context = super.customizeContext(context, request);
    return AppServerBridge.init(context, false);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jetty-8.0";
  }
}
