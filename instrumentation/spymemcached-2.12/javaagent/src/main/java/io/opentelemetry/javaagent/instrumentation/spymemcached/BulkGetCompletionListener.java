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
    MemcachedNode handlingNode = getRepresentativeNodeFromConnection(connection);
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName, handlingNode);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new BulkGetCompletionListener(parentContext, request);
  }

  @Nullable
  private static MemcachedNode getRepresentativeNodeFromConnection(MemcachedConnection connection) {
    try {
      // Strategy: Get the "most representative" node for bulk operations
      // We choose the last active node in the list, which often represents
      // the most recently added or most stable node in the cluster
      Collection<MemcachedNode> allNodes = connection.getLocator().getAll();

      MemcachedNode lastActiveNode = null;
      MemcachedNode fallbackNode = null;

      for (MemcachedNode node : allNodes) {
        if (fallbackNode == null) {
          fallbackNode = node;
        }

        if (node.isActive()) {
          lastActiveNode = node;
        }
      }

      // Return the last active node, or fallback to the first node
      return lastActiveNode != null ? lastActiveNode : fallbackNode;
    } catch (RuntimeException e) {
      return null;
    }
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
