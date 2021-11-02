/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import ratpack.func.Action;

public class ActionWrapper<T> implements Action<T> {

  private final Action<T> delegate;
  private final Context parentContext;

  private ActionWrapper(Action<T> delegate, Context parentContext) {
    assert parentContext != null;
    this.delegate = delegate;
    this.parentContext = parentContext;
  }

  @Override
  public void execute(T t) throws Exception {
    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(Action<T> delegate) {
    if (delegate instanceof ActionWrapper) {
      return delegate;
    }
    Context context = Context.current();
    if (context == Context.root()) {
      // Skip wrapping, there is no need to propagate root context.
      return delegate;
    }
    return new ActionWrapper(delegate, context);
  }
}
