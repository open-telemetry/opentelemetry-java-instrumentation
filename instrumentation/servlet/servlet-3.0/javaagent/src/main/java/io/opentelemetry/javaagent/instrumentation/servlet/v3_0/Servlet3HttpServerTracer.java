/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerTracer extends ServletHttpServerTracer<HttpServletResponse> {

  private static final Servlet3HttpServerTracer TRACER = new Servlet3HttpServerTracer();

  public static Servlet3HttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-3.0";
  }

  @Override
  protected Integer peerPort(HttpServletRequest connection) {
    return connection.getRemotePort();
  }

  @Override
  protected int responseStatus(HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  protected boolean isResponseCommitted(HttpServletResponse response) {
    return response.isCommitted();
  }

  public void onTimeout(Context context, long timeout) {
    Span span = Span.fromContext(context);
    span.setStatus(StatusCode.ERROR);
    span.setAttribute("servlet.timeout", timeout);
    span.end();
  }

  /*
  Given request already has a context associated with it.
  As there should not be nested spans of kind SERVER, we should NOT create a new span here.

  But it may happen that there is no span in current Context or it is from a different trace.
  E.g. in case of async servlet request processing we create span for incoming request in one thread,
  but actual request continues processing happens in another thread.
  Depending on servlet container implementation, this processing may again arrive into this method.
  E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

  In this case we have to put the span from the request into current context before continuing.
  */
  public static boolean needsRescoping(Context attachedContext) {
    return !sameTrace(Span.fromContext(Context.current()), Span.fromContext(attachedContext));
  }

  private static boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan
        .getSpanContext()
        .getTraceIdAsHexString()
        .equals(otherSpan.getSpanContext().getTraceIdAsHexString());
  }
}
