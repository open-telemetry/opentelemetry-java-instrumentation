/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spring.scheduling;

import static io.opentelemetry.instrumentation.auto.spring.scheduling.SpringSchedulingTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;

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
    Span span = TRACER.startSpan(TRACER.spanNameOnRun(runnable), Kind.INTERNAL);

    try (Scope ignored = currentContextWith(span)) {
      runnable.run();
      TRACER.end(span);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    }
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
