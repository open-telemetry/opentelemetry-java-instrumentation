/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;

public class JettyHttpServerTracer extends Servlet3HttpServerTracer {
  private static final JettyHttpServerTracer TRACER = new JettyHttpServerTracer();

  public static JettyHttpServerTracer tracer() {
    return TRACER;
  }

  public Context startServerSpan(HttpServletRequest request, Method instrumentedMethod) {
    Context context =
        AppServerBridge.init(startSpan(request, request, request, instrumentedMethod), false);

    // context must be reattached, because it has new attributes compared to the one returned from
    // startSpan().
    attachServerContext(context, request);
    return context;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jetty";
  }
}
