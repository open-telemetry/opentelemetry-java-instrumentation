/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;

public class TracingMaybe<T> extends Maybe<T> {

  private final MaybeSource<T> actual;
  private final Context parentSpan;

  public TracingMaybe(final MaybeSource<T> actual, final Context parentSpan) {
    this.actual = actual;
    this.parentSpan = parentSpan;
  }

  @Override
  protected void subscribeActual(final MaybeObserver<? super T> observer) {
    actual.subscribe(new TracingMaybeObserver<>(observer, parentSpan));
  }
}
