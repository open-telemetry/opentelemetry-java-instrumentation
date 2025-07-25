/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.apachecamel;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

public class ContextWithScope {
  @Nullable private final ContextWithScope parent;
  @Nullable private final Context context;
  @Nullable private final Scope scope;

  public ContextWithScope(ContextWithScope parent, Context context, Scope scope) {
    this.parent = parent;
    this.context = context;
    this.scope = scope;
  }

  public static ContextWithScope activate(ContextWithScope parent, Context context) {
    Scope scope = context != null ? context.makeCurrent() : null;
    return new ContextWithScope(parent, context, scope);
  }

  public Context getContext() {
    return context;
  }

  public ContextWithScope getParent() {
    return parent;
  }

  public void deactivate() {
    if (scope == null) {
      return;
    }
    scope.close();
  }

  @Override
  public String toString() {
    return "ContextWithScope [context=" + context + ", scope=" + scope + "]";
  }
}
