/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import org.reactivestreams.Subscriber;

public class TracingConnectableFlowable<T> extends ConnectableFlowable<T> {

  private final ConnectableFlowable<T> source;
  private final Context parentSpan;

  public TracingConnectableFlowable(final ConnectableFlowable<T> source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @Override
  public void connect(final @NonNull Consumer<? super Disposable> connection) {
    try (Scope scope = parentSpan.makeCurrent()) {
      source.connect(connection);
    }
  }

  @Override
  protected void subscribeActual(final Subscriber<? super T> s) {
    try (Scope scope = parentSpan.makeCurrent()) {
      if (s instanceof ConditionalSubscriber) {
        source.subscribe(
            new TracingConditionalSubscriber<>((ConditionalSubscriber<? super T>) s, parentSpan));
      } else {
        source.subscribe(new TracingSubscriber<>(s, parentSpan));
      }
    }
  }
}
