/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.SpringSchedulingSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class SpringSchedulingRunnableWrapper implements Runnable {
  private final Runnable runnable;

  private SpringSchedulingRunnableWrapper(Runnable runnable) {
    this.runnable = runnable;
  }

  @Override
  public void run() {
    if (runnable == null) {
      return;
    }

    Context parentContext = currentContext();
    if (!instrumenter().shouldStart(parentContext, runnable)) {
      runnable.run();
      return;
    }

    Context context = instrumenter().start(parentContext, runnable);
    // remember the context, so it could be reused in error handler
    TaskContextHolder.set(context);
    try (Scope ignored = context.makeCurrent()) {
      runnable.run();
    } catch (Throwable t) {
      instrumenter().end(context, runnable, null, t);
      throw t;
    }
    instrumenter().end(context, runnable, null, null);
  }

  public static Runnable wrapIfNeeded(Runnable task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    if (task instanceof SpringSchedulingRunnableWrapper) {
      return task;
    }
    return new SpringSchedulingRunnableWrapper(task);
  }
}
