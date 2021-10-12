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
import net.spy.memcached.internal.BulkGetFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

public class BulkGetCompletionListener extends CompletionListener<BulkGetFuture<?>>
    implements net.spy.memcached.internal.BulkGetCompletionListener {

  private BulkGetCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Nullable
  public static BulkGetCompletionListener create(
      Context parentContext, MemcachedConnection connection, String methodName) {
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new BulkGetCompletionListener(parentContext, request);
  }

  @Override
  public void onComplete(BulkGetFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, BulkGetFuture<?> future)
      throws ExecutionException, InterruptedException {
    /*
    Note: for now we do not have an affective way of representing results of bulk operations,
    i.e. we cannot say that we got 4 hits out of 10. So we will just ignore results for now.
    */
    future.get();
  }
}
