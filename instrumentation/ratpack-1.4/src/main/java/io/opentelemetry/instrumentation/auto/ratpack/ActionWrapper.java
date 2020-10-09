/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.ratpack;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;

public class ActionWrapper<T> implements Action<T> {

  private static final Logger log = LoggerFactory.getLogger(ActionWrapper.class);

  private final Action<T> delegate;
  private final Context parentContext;

  private ActionWrapper(Action<T> delegate, Context parentContext) {
    assert parentContext != null;
    this.delegate = delegate;
    this.parentContext = parentContext;
  }

  @Override
  public void execute(T t) throws Exception {
    try (Scope ignored = withScopedContext(parentContext)) {
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(Action<T> delegate) {
    if (delegate instanceof ActionWrapper) {
      return delegate;
    }
    log.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, Context.current());
  }
}
