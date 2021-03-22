/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v2_2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
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
    Context context = startSpan(request, getSpanName(request));
    // server span name shouldn't be update when server span was created by servlet 2.2
    // instrumentation
    ServletSpanNaming.setServletUpdatedServerSpanName(context);
    return context;
  }

  public Context updateContext(Context context, HttpServletRequest request) {
    Span span = ServerSpan.fromContextOrNull(context);
    if (span != null && ServletSpanNaming.shouldUpdateServerSpanName(context)) {
      span.updateName(getSpanName(request));
      ServletSpanNaming.setServletUpdatedServerSpanName(context);
    }

    return super.updateContext(context, request);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-2.2";
  }
}
