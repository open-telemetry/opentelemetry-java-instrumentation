/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spymemcached;

import io.opentelemetry.trace.Span;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.GetFuture;

public class GetCompletionListener extends CompletionListener<GetFuture<?>>
    implements net.spy.memcached.internal.GetCompletionListener {
  public GetCompletionListener(MemcachedConnection connection, String methodName) {
    super(connection, methodName);
  }

  @Override
  public void onComplete(GetFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, GetFuture<?> future)
      throws ExecutionException, InterruptedException {
    Object result = future.get();
    setResultTag(span, result != null);
  }
}
