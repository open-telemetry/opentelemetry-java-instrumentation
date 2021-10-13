/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;

public class ActionWrapper<T> implements Action<T> {

  private static final Logger logger = LoggerFactory.getLogger(ActionWrapper.class);

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
      return delegate;
    }
    logger.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, context);
  }
}
