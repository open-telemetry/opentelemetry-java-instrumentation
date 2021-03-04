/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class LibertyHttpServerTracer extends Servlet3HttpServerTracer {
  private static final LibertyHttpServerTracer TRACER = new LibertyHttpServerTracer();

  public static LibertyHttpServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(HttpServletRequest request) {
    return startSpan(request, "HTTP " + request.getMethod());
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.liberty";
  }
}
