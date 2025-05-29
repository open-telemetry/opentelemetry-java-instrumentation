/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.javax;

import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

public abstract class JavaxServletAccessor<R> implements ServletAccessor<HttpServletRequest, R> {
  @Override
  public String getRequestContextPath(HttpServletRequest request) {
    return request.getContextPath();
  }

  @Override
  public String getRequestScheme(HttpServletRequest request) {
    return request.getScheme();
  }

  @Override
  public String getRequestUri(HttpServletRequest request) {
    return request.getRequestURI();
  }

  @Override
  public String getRequestQueryString(HttpServletRequest request) {
    return request.getQueryString();
  }

  @Override
  public Object getRequestAttribute(HttpServletRequest request, String name) {
    return request.getAttribute(name);
  }

  @Override
  public void setRequestAttribute(HttpServletRequest request, String name, Object value) {
    request.setAttribute(name, value);
  }

  @Override
  public String getRequestProtocol(HttpServletRequest request) {
    return request.getProtocol();
  }

  @Override
  public String getRequestMethod(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  public String getRequestRemoteAddr(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  public String getRequestHeader(HttpServletRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public List<String> getRequestHeaderValues(HttpServletRequest request, String name) {
    @SuppressWarnings("unchecked")
    Enumeration<String> values = request.getHeaders(name);
    return values == null ? Collections.emptyList() : Collections.list(values);
  }

  @Override
  public Iterable<String> getRequestHeaderNames(HttpServletRequest httpServletRequest) {
    @SuppressWarnings("unchecked")
    Enumeration<String> names = httpServletRequest.getHeaderNames();
    return Collections.list(names);
  }

  @Override
  public List<String> getRequestParameterValues(
      HttpServletRequest httpServletRequest, String name) {
    String[] values = httpServletRequest.getParameterValues(name);
    return values == null ? Collections.emptyList() : Arrays.asList(values);
  }

  @Override
  public String getRequestServletPath(HttpServletRequest request) {
    return request.getServletPath();
  }

  @Override
  public String getRequestPathInfo(HttpServletRequest request) {
    return request.getPathInfo();
  }

  @Override
  public Principal getRequestUserPrincipal(HttpServletRequest request) {
    return request.getUserPrincipal();
  }

  @Override
  public boolean isServletException(Throwable throwable) {
    return throwable instanceof ServletException;
  }
}
