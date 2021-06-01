/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletHttpServerTracer;
import jakarta.servlet.http.HttpServletRequest;

public class Jetty11HttpServerTracer extends JakartaServletHttpServerTracer {
  private static final Jetty11HttpServerTracer TRACER = new Jetty11HttpServerTracer();

  public static Jetty11HttpServerTracer tracer() {
    return TRACER;
  }

  public Context startServerSpan(HttpServletRequest request) {
    return startSpan(request, "HTTP " + request.getMethod(), /* servlet= */ false);
  }

  @Override
  protected Context customizeContext(Context context, HttpServletRequest request) {
    context = super.customizeContext(context, request);
    return AppServerBridge.init(context, /* shouldRecordException= */ false);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jetty-11.0";
  }
}
