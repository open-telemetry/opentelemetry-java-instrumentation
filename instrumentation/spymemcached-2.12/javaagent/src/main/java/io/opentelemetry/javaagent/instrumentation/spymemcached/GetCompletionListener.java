/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.FUTURE_OPERATION;
import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.ops.Operation;

public class GetCompletionListener extends CompletionListener<GetFuture<?>>
    implements net.spy.memcached.internal.GetCompletionListener {

  private GetCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Nullable
  public static GetCompletionListener create(
      Context parentContext,
      MemcachedConnection connection,
      String methodName,
      GetFuture<?> future) {
    MemcachedNode handlingNode = extractHandlingNodeFromFuture(future);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName, handlingNode);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new GetCompletionListener(parentContext, request);
  }

  @Nullable
  private static MemcachedNode extractHandlingNodeFromFuture(GetFuture<?> future) {
    Operation operation = FUTURE_OPERATION.get(future);
    if (operation != null) {
      return operation.getHandlingNode();
    }
    return null;
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
