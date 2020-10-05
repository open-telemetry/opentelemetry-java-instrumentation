/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rxjava;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscription;

public class SpanFinishingSubscription implements Subscription {
  private final BaseTracer tracer;
  private final AtomicReference<Span> spanRef;

  public SpanFinishingSubscription(BaseTracer tracer, AtomicReference<Span> spanRef) {
    this.tracer = tracer;
    this.spanRef = spanRef;
  }

  @Override
  public void unsubscribe() {
    Span span = spanRef.getAndSet(null);
    if (span != null) {
      tracer.end(span);
    }
  }

  @Override
  public boolean isUnsubscribed() {
    return spanRef.get() == null;
  }
}
