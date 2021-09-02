/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v2_2;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletHttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class Servlet2HttpServerTracer extends JavaxServletHttpServerTracer<ResponseWithStatus> {
  private static final Servlet2HttpServerTracer TRACER = new Servlet2HttpServerTracer();

  private final ServerSpanNameSupplier<HttpServletRequest> serverSpanName =
      (context, request) -> getSpanName(request);

  public Servlet2HttpServerTracer() {
    super(Servlet2Accessor.INSTANCE);
  }

  public static Servlet2HttpServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(HttpServletRequest request) {
    return startSpan(request, getSpanName(request), true);
  }

  @Override
  public Context updateContext(Context context, HttpServletRequest request) {
    ServerSpanNaming.updateServerSpanName(context, SERVLET, serverSpanName, request);
    return super.updateContext(context, request);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.servlet-2.2";
  }
}
