/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.annotations.NonNull;

public class TracingSingle<T> extends Single<T> {

  private final SingleSource<T> source;
  private final Context parentSpan;

  public TracingSingle(final SingleSource<T> source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @Override
  protected void subscribeActual(@NonNull SingleObserver<? super T> singleObserver) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      source.subscribe(new TracingSingleObserver<>(singleObserver, parentSpan));
    }
  }
}
