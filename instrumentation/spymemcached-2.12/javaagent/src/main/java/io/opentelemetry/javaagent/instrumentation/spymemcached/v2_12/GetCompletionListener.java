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
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.ops.OperationStatus;

public class GetCompletionListener extends CompletionListener<GetFuture<?>>
    implements net.spy.memcached.internal.GetCompletionListener {

  @Nullable
  public static GetCompletionListener create(
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
    return new GetCompletionListener(parentContext, request);
  }

  private GetCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Override
  public void onComplete(GetFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, GetFuture<?> future)
      throws ExecutionException, InterruptedException {
    Object result = future.get();
    OperationStatus status = future.getStatus();
    if (status != null && !status.isSuccess()) {
      if (future.isCancelled()) {
        throw new CancellationException(status.getMessage());
      }
      throw new ExecutionException(new RuntimeException(status.getMessage()));
    }
    setResultTag(span, result != null);
  }
}
