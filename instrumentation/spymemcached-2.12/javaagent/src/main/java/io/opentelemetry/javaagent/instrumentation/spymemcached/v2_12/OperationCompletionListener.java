/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.OperationStatus;

public class OperationCompletionListener extends CompletionListener<OperationFuture<?>>
    implements net.spy.memcached.internal.OperationCompletionListener {

  @Nullable
  public static OperationCompletionListener create(
      Context parentContext,
      MemcachedConnection connection,
      String methodName,
      String methodDescriptor,
      Object[] args) {
    SpymemcachedRequest request =
        SpymemcachedRequest.create(connection, methodName, methodDescriptor, args);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new OperationCompletionListener(parentContext, request);
  }

  private OperationCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Override
  public void onComplete(OperationFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, OperationFuture<?> future)
      throws ExecutionException, InterruptedException {
    future.get();
    OperationStatus status = future.getStatus();
    if (status != null && !status.isSuccess()) {
      if (future.isCancelled()) {
        throw new CancellationException(status.getMessage());
      }
      throw new ExecutionException(new RuntimeException(status.getMessage()));
    }
  }
}
