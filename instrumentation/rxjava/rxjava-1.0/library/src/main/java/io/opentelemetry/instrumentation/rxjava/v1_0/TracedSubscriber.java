/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

final class TracedSubscriber<T, REQUEST> extends Subscriber<T> {

  private final Subscriber<? super T> delegate;
  private final Instrumenter<REQUEST, ?> instrumenter;
  private final AtomicReference<Context> contextRef;
  private final REQUEST request;
  private final Context parentContext;

  TracedSubscriber(
      Subscriber<? super T> delegate,
      Instrumenter<REQUEST, ?> instrumenter,
      AtomicReference<Context> contextRef,
      REQUEST request,
      Context parentContext) {
    this.delegate = delegate;
    this.instrumenter = instrumenter;
    this.contextRef = contextRef;
    this.request = request;
    this.parentContext = parentContext;

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
      try (Scope ignored = context.makeCurrent()) {
        delegate.onCompleted();
      } catch (Throwable t) {
        instrumenter.end(context, request, null, t);
        throw t;
      }
      instrumenter.end(context, request, null, null);
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(Throwable e) {
    Context operationContext = contextRef.getAndSet(null);
    Context callbackContext = parentContext;
    if (operationContext != null) {
      if (shouldRetainOperationContext(operationContext)) {
        callbackContext = operationContext;
      }
      instrumenter.end(operationContext, request, null, e);
    }
    try (Scope ignored = callbackContext.makeCurrent()) {
      delegate.onError(e);
    }
  }

  private static boolean shouldRetainOperationContext(Context operationContext) {
    // Hystrix creates its fallback observable in onError and captures the current
    // context as its parent. Retain the operation context when the error arrives
    // under it, including executor-derived contexts with the same valid SpanContext.
    Context currentContext = Context.current();
    SpanContext operationSpanContext = Span.fromContext(operationContext).getSpanContext();
    return currentContext == operationContext
        || (operationSpanContext.isValid()
            && operationSpanContext.equals(Span.fromContext(currentContext).getSpanContext()));
  }
}
