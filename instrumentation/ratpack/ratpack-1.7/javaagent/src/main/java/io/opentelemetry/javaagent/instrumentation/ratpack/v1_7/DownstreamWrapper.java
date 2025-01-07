/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import ratpack.exec.Downstream;

public final class DownstreamWrapper<T> implements Downstream<T> {

  private final Downstream<T> delegate;
  private final Context parentContext;

  private DownstreamWrapper(Downstream<T> delegate, Context parentContext) {
    assert parentContext != null;
    this.delegate = delegate;
    this.parentContext = parentContext;
  }

  @Override
  public void success(T value) {
    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.success(value);
    }
  }

  @Override
  public void error(Throwable throwable) {
    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.error(throwable);
    }
  }

  @Override
  public void complete() {
    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.complete();
    }
  }

  public static <T> Downstream<T> wrapIfNeeded(Downstream<T> delegate) {
    if (delegate instanceof DownstreamWrapper) {
      return delegate;
    }
    Context context = Context.current();
    if (context == Context.root()) {
      // Skip wrapping, there is no need to propagate root context.
      return delegate;
    }
    return new DownstreamWrapper<>(delegate, context);
  }
}
