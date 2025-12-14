/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.internal.BulkGetFuture;

public class BulkGetCompletionListener extends CompletionListener<BulkGetFuture<?>>
    implements net.spy.memcached.internal.BulkGetCompletionListener {

  private BulkGetCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Nullable
  public static BulkGetCompletionListener create(
      Context parentContext, MemcachedConnection connection, String methodName) {
    MemcachedNode handlingNode = getNodeFromConnection(connection);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName, handlingNode);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new BulkGetCompletionListener(parentContext, request);
  }

  @Nullable
  private static MemcachedNode getNodeFromConnection(MemcachedConnection connection) {
    Collection<MemcachedNode> allNodes = connection.getLocator().getAll();
    if (allNodes.size() == 1) {
      return allNodes.iterator().next();
    }
    // For multiple nodes, return null - bulk operations span multiple servers
    // and we cannot accurately attribute to a single server
    return null;
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
