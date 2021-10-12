/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.GetFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

public class GetCompletionListener extends CompletionListener<GetFuture<?>>
    implements net.spy.memcached.internal.GetCompletionListener {

  private GetCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Nullable
  public static GetCompletionListener create(
      Context parentContext, MemcachedConnection connection, String methodName) {
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new GetCompletionListener(parentContext, request);
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
