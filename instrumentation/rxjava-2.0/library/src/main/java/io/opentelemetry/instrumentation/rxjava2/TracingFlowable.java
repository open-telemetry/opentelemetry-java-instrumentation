/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.reactivex.Flowable;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public class TracingFlowable<T> extends Flowable<T> {

  private final Publisher<T> source;
  private final Context parentSpan;

  public TracingFlowable(final Publisher<T> source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void subscribeActual(Subscriber<? super T> s) {
    if (s instanceof ConditionalSubscriber) {
      source.subscribe(
          new TracingConditionalSubscriber<>((ConditionalSubscriber<? super T>) s, parentSpan));
    } else {
      source.subscribe(new TracingSubscriber<>(s, parentSpan));
    }
  }
}
