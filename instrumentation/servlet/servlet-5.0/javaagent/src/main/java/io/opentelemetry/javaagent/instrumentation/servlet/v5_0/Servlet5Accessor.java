/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAsyncListener;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class Servlet5Accessor
    implements ServletAccessor<HttpServletRequest, HttpServletResponse>,
        HttpServerResponseMutator<HttpServletResponse> {
  public static final Servlet5Accessor INSTANCE = new Servlet5Accessor();

  private Servlet5Accessor() {}

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
  public Integer getRequestRemotePort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  public String getRequestLocalAddr(HttpServletRequest request) {
    return request.getLocalAddr();
  }

  @Override
  public Integer getRequestLocalPort(HttpServletRequest request) {
    return request.getLocalPort();
  }

  @Override
  public String getRequestHeader(HttpServletRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public List<String> getRequestHeaderValues(HttpServletRequest request, String name) {
    Enumeration<String> values = request.getHeaders(name);
    return values == null ? Collections.emptyList() : Collections.list(values);
  }

  @Override
  public Iterable<String> getRequestHeaderNames(HttpServletRequest httpServletRequest) {
    return Collections.list(httpServletRequest.getHeaderNames());
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
  public void addRequestAsyncListener(
      HttpServletRequest request,
      ServletAsyncListener<HttpServletResponse> listener,
      Object response) {
    if (response instanceof HttpServletResponse) {
      request
          .getAsyncContext()
          .addListener(new Listener(listener), request, (HttpServletResponse) response);
    }
  }

  @Override
  public int getResponseStatus(HttpServletResponse response) {
    return response.getStatus();
  }

  @Override
  public List<String> getResponseHeaderValues(HttpServletResponse response, String name) {
    Collection<String> values = response.getHeaders(name);
    if (values == null) {
      return Collections.emptyList();
    }
    if (values instanceof List) {
      return (List<String>) values;
    }
    return new ArrayList<>(values);
  }

  @Override
  public boolean isResponseCommitted(HttpServletResponse response) {
    return response.isCommitted();
  }

  @Override
  public boolean isServletException(Throwable throwable) {
    return throwable instanceof ServletException;
  }

  @Override
  public void appendHeader(HttpServletResponse response, String name, String value) {
    response.addHeader(name, value);
  }

  private static class Listener implements AsyncListener {
    private final ServletAsyncListener<HttpServletResponse> listener;

    private Listener(ServletAsyncListener<HttpServletResponse> listener) {
      this.listener = listener;
    }

    @Override
    public void onComplete(AsyncEvent event) {
      listener.onComplete((HttpServletResponse) event.getSuppliedResponse());
    }

    @Override
    public void onTimeout(AsyncEvent event) {
      listener.onTimeout(event.getAsyncContext().getTimeout());
    }

    @Override
    public void onError(AsyncEvent event) {
      listener.onError(event.getThrowable(), (HttpServletResponse) event.getSuppliedResponse());
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
      event
          .getAsyncContext()
          .addListener(this, event.getSuppliedRequest(), event.getSuppliedResponse());
    }
  }
}
