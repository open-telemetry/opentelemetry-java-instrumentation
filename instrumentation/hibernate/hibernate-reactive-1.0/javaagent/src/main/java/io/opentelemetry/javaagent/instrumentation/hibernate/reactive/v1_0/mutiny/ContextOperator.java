/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.mutiny;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.UniOperator;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.subscription.UniSubscription;

public final class ContextOperator<T> extends UniOperator<T, T> {
  private final Context context;

  public ContextOperator(Uni<? extends T> upstream, Context context) {
    super(upstream);
    this.context = context;
  }

  public static <T> Uni<T> plug(Uni<T> uni) {
    if (uni instanceof ContextOperator) {
      return uni;
    }
    Context parentContext = Context.current();
    if (parentContext == Context.root()) {
      return uni;
    }

    return uni.plug(u -> new ContextOperator<>(u, parentContext));
  }

  @Override
  public void subscribe(UniSubscriber<? super T> downstream) {
    try (Scope ignore = context.makeCurrent()) {
      upstream().subscribe().withSubscriber(new ContextSubscriber<>(downstream, context));
    }
  }

  private static class ContextSubscriber<T> implements UniSubscriber<T> {
    private final UniSubscriber<? super T> downstream;
    private final Context context;

    private ContextSubscriber(UniSubscriber<? super T> downstream, Context context) {
      this.downstream = downstream;
      this.context = context;
    }

    @Override
    public void onSubscribe(UniSubscription uniSubscription) {
      try (Scope ignore = context.makeCurrent()) {
        downstream.onSubscribe(uniSubscription);
      }
    }

    @Override
    public void onItem(T t) {
      try (Scope ignore = context.makeCurrent()) {
        downstream.onItem(t);
      }
    }

    @Override
    public void onFailure(Throwable throwable) {
      try (Scope ignore = context.makeCurrent()) {
        downstream.onFailure(throwable);
      }
    }
  }
}
