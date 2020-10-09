/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.trace.Span;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.OperationFuture;

public class OperationCompletionListener
    extends CompletionListener<OperationFuture<? extends Object>>
    implements net.spy.memcached.internal.OperationCompletionListener {
  public OperationCompletionListener(MemcachedConnection connection, String methodName) {
    super(connection, methodName);
  }

  @Override
  public void onComplete(OperationFuture<? extends Object> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, OperationFuture<? extends Object> future)
      throws ExecutionException, InterruptedException {
    future.get();
  }
}
