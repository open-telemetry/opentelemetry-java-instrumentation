/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rxjava;

import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscription;

public class SpanFinishingSubscription implements Subscription {
  private final BaseTracer tracer;
  private final AtomicReference<Context> contextRef;

  public SpanFinishingSubscription(BaseTracer tracer, AtomicReference<Context> contextRef) {
    this.tracer = tracer;
    this.contextRef = contextRef;
  }

  @Override
  public void unsubscribe() {
    Context context = contextRef.getAndSet(null);
    if (context != null) {
      Span span = getSpan(context);
      if (span.getContext().isValid()) {
        tracer.end(span);
      }
    }
  }

  @Override
  public boolean isUnsubscribed() {
    return contextRef.get() == null;
  }
}
