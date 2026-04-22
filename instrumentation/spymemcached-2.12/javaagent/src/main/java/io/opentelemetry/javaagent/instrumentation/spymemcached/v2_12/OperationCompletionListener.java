/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.OperationFuture;

public class OperationCompletionListener extends CompletionListener<OperationFuture<?>>
    implements net.spy.memcached.internal.OperationCompletionListener {

  @Nullable
  public static OperationCompletionListener create(
      Context parentContext, MemcachedConnection connection, String methodName) {
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName);
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
  }
}
