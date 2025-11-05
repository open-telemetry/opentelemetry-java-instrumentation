/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.servlet.v3_0.copied.Servlet3Singletons.helper;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/// Delegates all methods except [#start(Runnable) which wraps the [Runnable].
public class OtelAsyncContext implements AsyncContext {
  private final AsyncContext delegate;

  public OtelAsyncContext(AsyncContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public ServletRequest getRequest() {
    return delegate.getRequest();
  }

  @Override
  public ServletResponse getResponse() {
    return delegate.getResponse();
  }

  @Override
  public boolean hasOriginalRequestAndResponse() {
    return delegate.hasOriginalRequestAndResponse();
  }

  @Override
  public void dispatch() {
    delegate.dispatch();
  }

  @Override
  public void dispatch(String path) {
    delegate.dispatch(path);
  }

  @Override
  public void dispatch(ServletContext context, String path) {
    delegate.dispatch(context, path);
  }

  @Override
  public void complete() {
    delegate.complete();
  }

  @Override
  public void start(Runnable run) {
    delegate.start(helper().wrapAsyncRunnable(run));
  }

  @Override
  public void addListener(AsyncListener listener) {
    delegate.addListener(listener);
  }

  @Override
  public void addListener(
      AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
    delegate.addListener(listener, servletRequest, servletResponse);
  }

  @Override
  public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
    return delegate.createListener(clazz);
  }

  @Override
  public void setTimeout(long timeout) {
    delegate.setTimeout(timeout);
  }

  @Override
  public long getTimeout() {
    return delegate.getTimeout();
  }
}
