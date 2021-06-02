/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class Jetty8HttpServerTracer extends Servlet3HttpServerTracer {
  private static final Jetty8HttpServerTracer TRACER = new Jetty8HttpServerTracer();

  public static Jetty8HttpServerTracer tracer() {
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
    return "io.opentelemetry.javaagent.jetty-8.0";
  }
}
