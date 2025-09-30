/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;

public final class ContextPropagatingRunnable implements Runnable, WrappedRunnable {

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

  @Override
  public Runnable getDelegate() {
    return delegate;
  }
}
