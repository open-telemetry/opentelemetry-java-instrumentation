/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAsyncListener;
import io.opentelemetry.javaagent.instrumentation.servlet.javax.JavaxServletAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3Accessor extends JavaxServletAccessor<HttpServletResponse>
    implements HttpServerResponseMutator<HttpServletResponse> {
  public static final Servlet3Accessor INSTANCE = new Servlet3Accessor();

  private Servlet3Accessor() {}

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
