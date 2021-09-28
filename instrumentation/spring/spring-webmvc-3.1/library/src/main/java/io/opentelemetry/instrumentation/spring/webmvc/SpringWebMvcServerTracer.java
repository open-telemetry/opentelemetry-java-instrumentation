/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.instrumentation.servlet.javax.JavaxHttpServletRequestGetter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class SpringWebMvcServerTracer
    extends HttpServerTracer<
        HttpServletRequest, HttpServletResponse, HttpServletRequest, HttpServletRequest> {

  SpringWebMvcServerTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  protected String peerHostIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  protected TextMapGetter<HttpServletRequest> getGetter() {
    return JavaxHttpServletRequestGetter.GETTER;
  }

  @Override
  protected String scheme(HttpServletRequest request) {
    return request.getScheme();
  }

  @Override
  protected String host(HttpServletRequest request) {
    return request.getServerName() + ":" + request.getServerPort();
  }

  @Override
  protected String target(HttpServletRequest request) {
    String target = request.getPathInfo();
    String queryString = request.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  protected String requestHeader(HttpServletRequest httpServletRequest, String name) {
    return httpServletRequest.getHeader(name);
  }

  @Override
  protected int responseStatus(HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  protected void attachServerContext(Context context, HttpServletRequest request) {
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  protected String flavor(HttpServletRequest connection, HttpServletRequest request) {
    return connection.getProtocol();
  }

  @Override
  public Context getServerContext(HttpServletRequest request) {
    Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.spring-webmvc-3.1";
  }
}
