/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.OperationFuture;

public class OperationCompletionListener extends CompletionListener<OperationFuture<?>>
    implements net.spy.memcached.internal.OperationCompletionListener {
  public OperationCompletionListener(
      Context parentContext, MemcachedConnection connection, String methodName) {
    super(parentContext, connection, methodName);
  }

  @Override
  public void onComplete(OperationFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, OperationFuture<?> future)
      throws ExecutionException, InterruptedException {
    future.get();
  }
}
