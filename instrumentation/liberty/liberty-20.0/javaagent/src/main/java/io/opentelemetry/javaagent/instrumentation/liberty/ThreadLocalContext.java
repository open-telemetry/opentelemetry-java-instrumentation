/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final class ThreadLocalContext {

  private static final ThreadLocal<ThreadLocalContext> local = new ThreadLocal<>();

  private final HttpServletResponse response;
  private final ServletRequestContext<HttpServletRequest> requestContext;
  @Nullable private Context context;
  @Nullable private Scope scope;
  private boolean started;

  private ThreadLocalContext(HttpServletRequest request, HttpServletResponse response) {
    this.response = response;
    this.requestContext = new ServletRequestContext<>(request);
  }

  @Nullable
  Context getContext() {
    return context;
  }

  void setContext(Context context) {
    this.context = context;
  }

  @Nullable
  Scope getScope() {
    return scope;
  }

  void setScope(Scope scope) {
    this.scope = scope;
  }

  HttpServletRequest getRequest() {
    return requestContext.request();
  }

  ServletRequestContext<HttpServletRequest> getRequestContext() {
    return requestContext;
  }

  HttpServletResponse getResponse() {
    return response;
  }

  /**
   * Test whether span should be started.
   *
   * @return true when span should be started, false when span was already started
   */
  boolean startSpan() {
    boolean alreadyStarted = started;
    started = true;
    return !alreadyStarted;
  }

  static void startRequest(HttpServletRequest request, HttpServletResponse response) {
    ThreadLocalContext ctx = new ThreadLocalContext(request, response);
    local.set(ctx);
  }

  static ThreadLocalContext get() {
    return local.get();
  }

  static ThreadLocalContext endRequest() {
    ThreadLocalContext ctx = local.get();
    if (ctx != null) {
      local.remove();
    }
    return ctx;
  }
}
