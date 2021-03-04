/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Kubernetes instrumentation starts and ends spans in two different methods - there only way to
 * pass {@link Scope} between them is to use a thread local.
 */
public final class CurrentContextAndScope {
  private static final ThreadLocal<CurrentContextAndScope> CURRENT = new ThreadLocal<>();

  private final Context parentContext;
  private final Context context;
  private final Scope scope;

  private CurrentContextAndScope(Context parentContext, Context context, Scope scope) {
    this.parentContext = parentContext;
    this.context = context;
    this.scope = scope;
  }

  public static void set(Context parentContext, Context context) {
    CURRENT.set(new CurrentContextAndScope(parentContext, context, context.makeCurrent()));
  }

  @Nullable
  public static CurrentContextAndScope remove() {
    CurrentContextAndScope contextAndScope = CURRENT.get();
    CURRENT.remove();
    return contextAndScope;
  }

  @Nullable
  public static Context removeAndClose() {
    CurrentContextAndScope contextAndScope = remove();
    if (contextAndScope == null) {
      return null;
    }
    contextAndScope.scope.close();
    return contextAndScope.context;
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
}
