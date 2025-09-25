/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import java.util.concurrent.TimeUnit;

public final class ContextPropagatingRunnable implements Runnable {

  public static boolean shouldDecorateRunnable(Runnable task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    // note: it is always safe to decorate lambdas since downstream code cannot be expecting a
    // specific runnable implementation anyways
    return task.getClass().getName().contains("/") && !(task instanceof ContextPropagatingRunnable);
  }

  public static Runnable propagateContext(Runnable task, Context context) {
    return new ContextPropagatingRunnable(task, context);
  }
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);
  private final Runnable delegate;
  private final Context context;
  private final Long startObservation;
  private final DoubleHistogram pendingTimeHistogram;

  private ContextPropagatingRunnable(Runnable delegate, Context context) {
    this.delegate = delegate;
    this.context = ContextPropagationDebug.addDebugInfo(context, delegate);
    this.startObservation = System.nanoTime();

    this.pendingTimeHistogram =
        GlobalOpenTelemetry
            .getMeter("thread.pending.duration")
            .histogramBuilder("thread.pending.duration")
            .setUnit("s")
            .setDescription("Duration of HTTP client requests.")
            .build();
  }

  @Override
  public void run() {
    try (Scope ignored = context.makeCurrent()) {
      pendingTimeHistogram
          .record(System.nanoTime() - startObservation / NANOS_PER_S,
              Attributes.of(
                  AttributeKey.stringKey("thread"),
                  Thread.currentThread().getName()
              )
          );
      delegate.run();
    }
  }

  public Runnable unwrap() {
    return delegate;
  }
}
