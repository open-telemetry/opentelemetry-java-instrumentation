/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

final class McpContextMono<T> extends Mono<T> {
  private final Mono<? extends T> publisher;
  private final Context subscriptionContext;
  private final Context callbackContext;

  McpContextMono(
      Mono<? extends T> publisher, Context subscriptionContext, Context callbackContext) {
    this.publisher = publisher;
    this.subscriptionContext = subscriptionContext;
    this.callbackContext = callbackContext;
  }

  @Override
  public void subscribe(CoreSubscriber<? super T> actual) {
    // Mono.create invokes its callback outside subscriber signal scopes.
    try (Scope ignored = subscriptionContext.makeCurrent()) {
      publisher.subscribe(new ContextSubscriber<>(actual, subscriptionContext, callbackContext));
    }
  }

  private static final class ContextSubscriber<T> implements CoreSubscriber<T>, Subscription {
    private final CoreSubscriber<? super T> actual;
    private final Context subscriptionContext;
    private final Context callbackContext;
    private final reactor.util.context.Context reactorContext;
    private Subscription upstream;

    private ContextSubscriber(
        CoreSubscriber<? super T> actual, Context subscriptionContext, Context callbackContext) {
      this.actual = actual;
      this.subscriptionContext = subscriptionContext;
      this.callbackContext = callbackContext;
      reactorContext =
          ContextPropagationOperator.storeOpenTelemetryContext(
              actual.currentContext(), subscriptionContext);
    }

    @Override
    public reactor.util.context.Context currentContext() {
      return reactorContext;
    }

    @Override
    public void onSubscribe(Subscription upstream) {
      this.upstream = upstream;
      try (Scope ignored = callbackContext.makeCurrent()) {
        actual.onSubscribe(this);
      }
    }

    @Override
    public void onNext(T value) {
      try (Scope ignored = callbackContext.makeCurrent()) {
        actual.onNext(value);
      }
    }

    @Override
    public void onError(Throwable t) {
      try (Scope ignored = callbackContext.makeCurrent()) {
        actual.onError(t);
      }
    }

    @Override
    public void onComplete() {
      try (Scope ignored = callbackContext.makeCurrent()) {
        actual.onComplete();
      }
    }

    @Override
    public void request(long n) {
      try (Scope ignored = subscriptionContext.makeCurrent()) {
        upstream.request(n);
      }
    }

    @Override
    public void cancel() {
      try (Scope ignored = subscriptionContext.makeCurrent()) {
        upstream.cancel();
      }
    }
  }
}
