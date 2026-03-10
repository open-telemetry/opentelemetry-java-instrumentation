/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.sql;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.Completable;

public class CompletableWrapper<T> implements Completable<T> {
  private final Completable<T> delegate;
  private final Context context;

  private CompletableWrapper(Completable<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  public static <T> Completable<T> wrap(Completable<T> handler) {
    Context current = Context.current();
    if (handler != null && !(handler instanceof CompletableWrapper) && current != Context.root()) {
      handler = new CompletableWrapper<>(handler, current);
    }
    return handler;
  }

  @Override
  public void complete(T t, Throwable error) {
    try (Scope ignore = context.makeCurrent()) {
      delegate.complete(t, error);
    }
  }
}
