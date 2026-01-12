/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import java.util.concurrent.Callable;

public final class ContextPropagatingCallable<T> implements Callable<T> {

  public static <T> boolean shouldDecorateCallable(Callable<T> task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    // note: it is always safe to decorate lambdas since downstream code cannot be expecting a
    // specific runnable implementation anyways
    return task.getClass().getName().contains("/") && !(task instanceof ContextPropagatingCallable);
  }

  public static <T> Callable<T> propagateContext(Callable<T> task, Context context) {
    return new ContextPropagatingCallable<T>(task, context);
  }

  private final Callable<T> delegate;
  private final Context context;

  private ContextPropagatingCallable(Callable<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = ContextPropagationDebug.addDebugInfo(context, delegate);
  }

  @Override
  public T call() throws Exception {
    try (Scope ignored = context.makeCurrent()) {
      return delegate.call();
    }
  }
}
