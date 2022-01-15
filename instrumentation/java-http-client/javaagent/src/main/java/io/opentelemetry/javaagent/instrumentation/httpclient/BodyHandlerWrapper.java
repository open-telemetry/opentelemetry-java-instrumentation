/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public class BodyHandlerWrapper<T> implements BodyHandler<T> {
  private final BodyHandler<T> delegate;
  private final Context context;

  public BodyHandlerWrapper(BodyHandler<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  public BodySubscriber<T> apply(ResponseInfo responseInfo) {
    BodySubscriber<T> subscriber = delegate.apply(responseInfo);
    if (subscriber instanceof BodySubscriberWrapper) {
      return subscriber;
    }
    return new BodySubscriberWrapper<>(subscriber, context);
  }

  public static class BodySubscriberWrapper<T> implements BodySubscriber<T> {
    private final BodySubscriber<T> delegate;
    private final Context context;

    public BodySubscriberWrapper(BodySubscriber<T> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    public BodySubscriber<T> getDelegate() {
      return delegate;
    }

    @Override
    public CompletionStage<T> getBody() {
      return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
      try (Scope ignore = context.makeCurrent()) {
        delegate.onNext(item);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      try (Scope ignore = context.makeCurrent()) {
        delegate.onError(throwable);
      }
    }

    @Override
    public void onComplete() {
      try (Scope ignore = context.makeCurrent()) {
        delegate.onComplete();
      }
    }
  }
}
