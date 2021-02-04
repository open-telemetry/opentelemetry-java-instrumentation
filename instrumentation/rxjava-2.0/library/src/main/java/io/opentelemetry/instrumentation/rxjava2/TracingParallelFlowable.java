/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.annotations.NonNull;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Subscriber;

public class TracingParallelFlowable<T> extends ParallelFlowable<T> {

  private final ParallelFlowable<T> source;
  private final Context parentSpan;

  public TracingParallelFlowable(final ParallelFlowable<T> source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @Override
  public void subscribe(final @NonNull Subscriber<? super T>[] subscribers) {
    if (!validate(subscribers)) {
      return;
    }
    final int n = subscribers.length;
    @SuppressWarnings("unchecked")
    final Subscriber<? super T>[] parents = new Subscriber[n];
    for (int i = 0; i < n; i++) {
      final Subscriber<? super T> z = subscribers[i];
      if (z instanceof ConditionalSubscriber) {
        parents[i] =
            new TracingConditionalSubscriber<>((ConditionalSubscriber<? super T>) z, parentSpan);
      } else {
        parents[i] = new TracingSubscriber<>(z, parentSpan);
      }
    }
    try (Scope scope = parentSpan.makeCurrent()) {
      source.subscribe(parents);
    }
  }

  @Override
  public int parallelism() {
    return source.parallelism();
  }
}
