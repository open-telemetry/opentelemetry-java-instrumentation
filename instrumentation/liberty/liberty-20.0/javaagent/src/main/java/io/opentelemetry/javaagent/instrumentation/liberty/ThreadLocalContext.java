/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ThreadLocalContext {

  private static final ThreadLocal<ThreadLocalContext> local = new ThreadLocal<>();

  private final HttpServletResponse response;
  private final ServletRequestContext<HttpServletRequest> requestContext;
  private Context context;
  private Scope scope;
  private boolean started;

  private ThreadLocalContext(HttpServletRequest request, HttpServletResponse response) {
    this.response = response;
    this.requestContext = new ServletRequestContext<>(request);
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  public Scope getScope() {
    return scope;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
  }

  public HttpServletRequest getRequest() {
    return requestContext.request();
  }

  public ServletRequestContext<HttpServletRequest> getRequestContext() {
    return requestContext;
  }

  public HttpServletResponse getResponse() {
    return response;
  }

  /**
   * Test whether span should be started.
   *
   * @return true when span should be started, false when span was already started
   */
  public boolean startSpan() {
    boolean b = started;
    started = true;
    return !b;
  }

  public static void startRequest(HttpServletRequest request, HttpServletResponse response) {
    ThreadLocalContext ctx = new ThreadLocalContext(request, response);
    local.set(ctx);
  }

  public static ThreadLocalContext get() {
    return local.get();
  }

  public static ThreadLocalContext endRequest() {
    ThreadLocalContext ctx = local.get();
    if (ctx != null) {
      local.remove();
    }
    return ctx;
  }
}
