/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rxjava;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

public class TracedSubscriber<T> extends Subscriber<T> {

  private final AtomicReference<Span> spanRef;
  private final Subscriber<T> delegate;
  private final BaseTracer tracer;

  // TODO pass the whole context here, not just span
  public TracedSubscriber(Span span, Subscriber<T> delegate, BaseTracer tracer) {
    spanRef = new AtomicReference<>(span);
    this.delegate = delegate;
    this.tracer = tracer;
    SpanFinishingSubscription subscription = new SpanFinishingSubscription(tracer, spanRef);
    delegate.add(subscription);
  }

  @Override
  public void onStart() {
    Span span = spanRef.get();
    if (span != null) {
      try (Scope ignored = currentContextWith(span)) {
        delegate.onStart();
      }
    } else {
      delegate.onStart();
    }
  }

  @Override
  public void onNext(T value) {
    Span span = spanRef.get();
    if (span != null) {
      try (Scope ignored = currentContextWith(span)) {
        delegate.onNext(value);
      } catch (Throwable e) {
        onError(e);
      }
    } else {
      delegate.onNext(value);
    }
  }

  @Override
  public void onCompleted() {
    Span span = spanRef.getAndSet(null);
    if (span != null) {
      boolean errored = false;
      try (Scope ignored = currentContextWith(span)) {
        delegate.onCompleted();
      } catch (Throwable e) {
        // Repopulate the spanRef for onError
        spanRef.compareAndSet(null, span);
        onError(e);
        errored = true;
      } finally {
        // finish called by onError, so don't finish again.
        if (!errored) {
          tracer.end(span);
        }
      }
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(Throwable e) {
    Span span = spanRef.getAndSet(null);
    if (span != null) {
      tracer.endExceptionally(span, e);
    }
    delegate.onError(e);
  }
}
