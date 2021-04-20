/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v2_2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletHttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class Servlet2HttpServerTracer extends JavaxServletHttpServerTracer<ResponseWithStatus> {
  private static final Servlet2HttpServerTracer TRACER = new Servlet2HttpServerTracer();

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
    updateServerSpanName(context, request);
    return super.updateContext(context, request);
  }

  private void updateServerSpanName(Context context, HttpServletRequest request) {
    Span span = ServerSpan.fromContextOrNull(context);
    if (span != null) {
      ServerSpanNaming serverSpanNaming = ServerSpanNaming.from(context);
      if (serverSpanNaming.shouldServletUpdateServerSpanName()) {
        span.updateName(getSpanName(request));
        serverSpanNaming.setServletUpdatedServerSpanName();
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-2.2";
  }
}
