/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.v20_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestContext;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ThreadLocalContext {

  private static final ScopedThreadValue<ThreadLocalContext> local = new ScopedThreadValue<>();

  private final HttpServletResponse response;
  private final ServletRequestContext<HttpServletRequest> requestContext;
  @Nullable private ThreadLocalContext previous;
  @Nullable private Context context;
  @Nullable private Scope scope;
  private boolean started;

  private ThreadLocalContext(HttpServletRequest request, HttpServletResponse response) {
    this.response = response;
    this.requestContext = new ServletRequestContext<>(request);
  }

  @Nullable
  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  @Nullable
  public Scope getScope() {
    return scope;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
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
    boolean alreadyStarted = started;
    started = true;
    return !alreadyStarted;
  }

  public static ThreadLocalContext startRequest(
      HttpServletRequest request, HttpServletResponse response) {
    ThreadLocalContext ctx = new ThreadLocalContext(request, response);
    ctx.previous = local.set(ctx);
    return ctx;
  }

  @Nullable
  public static ThreadLocalContext get() {
    return local.get();
  }

  public void endRequest() {
    local.restore(previous);
  }
}
