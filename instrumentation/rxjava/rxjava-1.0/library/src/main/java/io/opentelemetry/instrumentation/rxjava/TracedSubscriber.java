/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

final class TracedSubscriber<T, REQUEST> extends Subscriber<T> {

  private final Subscriber<T> delegate;
  private final Instrumenter<REQUEST, ?> instrumenter;
  private final AtomicReference<Context> contextRef;
  private final REQUEST request;

  TracedSubscriber(
      Subscriber<T> delegate,
      Instrumenter<REQUEST, ?> instrumenter,
      Context context,
      REQUEST request) {
    this.delegate = delegate;
    this.instrumenter = instrumenter;
    this.contextRef = new AtomicReference<>(context);
    this.request = request;

    delegate.add(new SpanFinishingSubscription<>(instrumenter, contextRef, request));
  }

  @Override
  public void onStart() {
    Context context = contextRef.get();
    if (context != null) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.onStart();
      }
    } else {
      delegate.onStart();
    }
  }

  @Override
  public void onNext(T value) {
    Context context = contextRef.get();
    if (context != null) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.onNext(value);
      }
    } else {
      delegate.onNext(value);
    }
  }

  @Override
  public void onCompleted() {
    Context context = contextRef.getAndSet(null);
    if (context != null) {
      Throwable error = null;
      try (Scope ignored = context.makeCurrent()) {
        delegate.onCompleted();
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        instrumenter.end(context, request, null, error);
      }
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(Throwable e) {
    Context context = contextRef.getAndSet(null);
    if (context != null) {
      instrumenter.end(context, request, null, e);
    }
    // TODO (trask) should this be wrapped in parent of context(?)
    delegate.onError(e);
  }
}
