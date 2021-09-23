/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import okhttp3.Request;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Kubernetes instrumentation starts and ends spans in two different methods - the only way to pass
 * state between them is to use a thread local.
 */
public final class CurrentState {
  private static final ThreadLocal<CurrentState> CURRENT = new ThreadLocal<>();

  private final Context parentContext;
  private final Context context;
  private final Scope scope;
  private final Request request;

  private CurrentState(Context parentContext, Context context, Scope scope, Request request) {
    this.parentContext = parentContext;
    this.context = context;
    this.scope = scope;
    this.request = request;
  }

  public static void set(Context parentContext, Context context, Scope scope, Request request) {
    CURRENT.set(new CurrentState(parentContext, context, scope, request));
  }

  @Nullable
  public static CurrentState remove() {
    CurrentState currentState = CURRENT.get();
    CURRENT.remove();
    return currentState;
  }

  public Context getParentContext() {
    return parentContext;
  }

  public Context getContext() {
    return context;
  }

  public Scope getScope() {
    return scope;
  }

  public Request getRequest() {
    return request;
  }
}
