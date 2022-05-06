/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscription;

final class SpanFinishingSubscription<REQUEST> implements Subscription {
  private final Instrumenter<REQUEST, ?> instrumenter;
  private final AtomicReference<Context> contextRef;
  private final REQUEST request;

  SpanFinishingSubscription(
      Instrumenter<REQUEST, ?> instrumenter, AtomicReference<Context> contextRef, REQUEST request) {
    this.instrumenter = instrumenter;
    this.contextRef = contextRef;
    this.request = request;
  }

  @Override
  public void unsubscribe() {
    Context context = contextRef.getAndSet(null);
    if (context != null) {
      instrumenter.end(context, request, null, null);
    }
  }

  @Override
  public boolean isUnsubscribed() {
    return contextRef.get() == null;
  }
}
