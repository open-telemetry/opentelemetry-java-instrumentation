/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;

public class TracingCompletable extends Completable {

  private final CompletableSource source;
  private final Context parentSpan;

  public TracingCompletable(final CompletableSource source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @Override
  protected void subscribeActual(CompletableObserver s) {
    source.subscribe(new TracingCompletableObserver(s, parentSpan));
  }
}
