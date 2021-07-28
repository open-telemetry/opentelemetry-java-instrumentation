/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.Handler;

public class HandlerWrapper<T> implements Handler<T> {
  private final Handler<T> delegate;
  private final Context context;

  private HandlerWrapper(Handler<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  public static <T> Handler<T> wrap(Handler<T> handler) {
    Context current = Context.current();
    if (handler != null && !(handler instanceof HandlerWrapper) && current != Context.root()) {
      handler = new HandlerWrapper<>(handler, current);
    }
    return handler;
  }

  @Override
  public void handle(T t) {
    try (Scope ignore = context.makeCurrent()) {
      delegate.handle(t);
    }
  }
}
