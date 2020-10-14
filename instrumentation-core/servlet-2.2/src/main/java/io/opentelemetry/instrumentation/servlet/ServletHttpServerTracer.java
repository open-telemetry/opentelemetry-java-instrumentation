/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.MoreAttributes;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ServletHttpServerTracer<RESPONSE>
    extends HttpServerTracer<HttpServletRequest, RESPONSE, HttpServletRequest, HttpServletRequest> {

  private static final Logger log = LoggerFactory.getLogger(ServletHttpServerTracer.class);

  @Override
  protected String url(HttpServletRequest httpServletRequest) {
    try {
      return new URI(
              httpServletRequest.getScheme(),
              null,
              httpServletRequest.getServerName(),
              httpServletRequest.getServerPort(),
              httpServletRequest.getRequestURI(),
              httpServletRequest.getQueryString(),
              null)
          .toString();
    } catch (URISyntaxException e) {
      log.debug("Failed to construct request URI", e);
      return null;
    }
  }

  @Override
  public Context getServerContext(HttpServletRequest request) {
    Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  @Override
  protected void attachServerContext(Context context, HttpServletRequest request) {
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  protected Integer peerPort(HttpServletRequest connection) {
    // HttpServletResponse doesn't have accessor for remote port prior to Servlet spec 3.0
    return null;
  }

  @Override
  protected String peerHostIP(HttpServletRequest connection) {
    return connection.getRemoteAddr();
  }

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  public void onRequest(Span span, HttpServletRequest request) {
    // we do this e.g. so that servlet containers can use these values in their access logs
    request.setAttribute("traceId", span.getContext().getTraceIdAsHexString());
    request.setAttribute("spanId", span.getContext().getSpanIdAsHexString());

    super.onRequest(span, request);
  }

  @Override
  protected Getter<HttpServletRequest> getGetter() {
    return HttpServletRequestGetter.GETTER;
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    Throwable result = throwable;
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      result = throwable.getCause();
    }
    return super.unwrapThrowable(result);
  }

  public void setPrincipal(Span span, HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    if (principal != null) {
      span.setAttribute(MoreAttributes.USER_NAME, principal.getName());
    }
  }

  @Override
  protected String flavor(HttpServletRequest connection, HttpServletRequest request) {
    return connection.getProtocol();
  }

  @Override
  protected String requestHeader(HttpServletRequest httpServletRequest, String name) {
    return httpServletRequest.getHeader(name);
  }
}
