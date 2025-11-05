/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.servlet.v3_0.copied.Servlet3Singletons.helper;

import io.opentelemetry.context.Context;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/// Wrapper around [HttpServletRequest] that attaches an async listener if [#startAsync()] is
/// invoked and a wrapper around [#getAsyncContext()] to capture exceptions from async [Runnable]s.
public class OtelHttpServletRequest extends HttpServletRequestWrapper {

  public OtelHttpServletRequest(HttpServletRequest request) {
    super(request);
  }

  @Override
  public AsyncContext getAsyncContext() {
    return new OtelAsyncContext(super.getAsyncContext());
  }

  @Override
  public AsyncContext startAsync() {
    try {
      return new OtelAsyncContext(super.startAsync());
    } finally {
      helper().attachAsyncListener(this, Context.current());
    }
  }

  @Override
  public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
    try {
      return new OtelAsyncContext(super.startAsync(servletRequest, servletResponse));
    } finally {
      helper().attachAsyncListener(this, Context.current());
    }
  }
}
