/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.Context;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.thread.Invocable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextAwareContentSource implements Content.Source, Invocable {

  public static Content.Source wrap(Content.Source source, Context context) {
    if (source instanceof ContextAwareContentSource) {
      return source;
    }
    return new ContextAwareContentSource(source, context);
  }

  private final Content.Source delegate;
  private final Context context;

  private ContextAwareContentSource(Content.Source delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  public InvocationType getInvocationType() {
    return Invocable.getInvocationType(delegate);
  }

  @Override
  public Content.Chunk read() {
    return delegate.read();
  }

  @Override
  public void demand(Runnable callback) {
    delegate.demand(Invocable.from(Invocable.getInvocationType(callback), context.wrap(callback)));
  }

  @Override
  public long getLength() {
    return delegate.getLength();
  }

  @Override
  public boolean rewind() {
    return delegate.rewind();
  }

  @Override
  public void fail(Throwable failure) {
    delegate.fail(failure);
  }

  @Override
  public void fail(Throwable failure, boolean close) {
    delegate.fail(failure, close);
  }
}
