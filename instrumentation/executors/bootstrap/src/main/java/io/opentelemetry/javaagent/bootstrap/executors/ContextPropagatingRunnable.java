/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;

public final class ContextPropagatingRunnable implements Runnable {

  public static boolean shouldDecorateRunnable(Runnable task) {
    // We wrap only lambda-generated classes that have not already been wrapped.
    // Lambda class names contain '/' which is not allowed in ordinary class names.
    // Note: it is always safe to decorate lambdas since downstream code cannot be expecting a
    // specific runnable implementation anyway.
    return task.getClass().getName().contains("/") && !(task instanceof ContextPropagatingRunnable);
  }

  public static Runnable propagateContext(Runnable task, Context context) {
    return new ContextPropagatingRunnable(task, context);
  }

  private final Runnable delegate;
  private final Context context;

  private ContextPropagatingRunnable(Runnable delegate, Context context) {
    this.delegate = delegate;
    this.context = ContextPropagationDebug.addDebugInfo(context, delegate);
  }

  @Override
  public void run() {
    try (Scope ignored = context.makeCurrent()) {
      delegate.run();
    }
  }

  public Runnable unwrap() {
    return delegate;
  }
}
