/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.stage;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.Function;

public final class FunctionWrapper<T, R> implements Function<T, R> {
  private final Function<T, R> delegate;
  private final Context context;

  private FunctionWrapper(Function<T, R> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  public static <T, R> Function<T, R> wrap(Function<T, R> function) {
    if (function instanceof FunctionWrapper) {
      return function;
    }
    Context context = Context.current();
    if (context == Context.root()) {
      return function;
    }

    return new FunctionWrapper<>(function, context);
  }

  @Override
  public R apply(T t) {
    try (Scope ignore = context.makeCurrent()) {
      return delegate.apply(t);
    }
  }
}
