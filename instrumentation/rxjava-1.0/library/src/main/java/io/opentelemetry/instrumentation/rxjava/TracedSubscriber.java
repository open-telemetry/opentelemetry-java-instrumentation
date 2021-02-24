/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

public class TracedSubscriber<T> extends Subscriber<T> {

  private final AtomicReference<Context> contextRef;
  private final Subscriber<T> delegate;
  private final BaseTracer tracer;

  public TracedSubscriber(Context context, Subscriber<T> delegate, BaseTracer tracer) {
    contextRef = new AtomicReference<>(context);
    this.delegate = delegate;
    this.tracer = tracer;
    SpanFinishingSubscription subscription = new SpanFinishingSubscription(tracer, contextRef);
    delegate.add(subscription);
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
        if (error != null) {
          tracer.endExceptionally(context, error);
        } else {
          tracer.end(context);
        }
      }
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(Throwable e) {
    Context context = contextRef.getAndSet(null);
    if (context != null) {
      tracer.endExceptionally(context, e);
    }
    // TODO (trask) should this be wrapped in parent of context(?)
    delegate.onError(e);
  }
}
