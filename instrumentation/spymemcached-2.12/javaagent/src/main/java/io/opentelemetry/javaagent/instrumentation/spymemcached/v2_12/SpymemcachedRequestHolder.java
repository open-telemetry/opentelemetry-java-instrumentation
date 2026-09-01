/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.context.ContextKey.named;
import static java.util.Collections.emptyMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.Operation;

public class SpymemcachedRequestHolder implements ImplicitContextKeyed {

  private static final ContextKey<SpymemcachedRequestHolder> KEY =
      named("opentelemetry-spymemcached-request-holder");
  private static final VirtualField<Operation, SpymemcachedRequestAssociations> REQUESTS =
      VirtualField.find(Operation.class, SpymemcachedRequestAssociations.class);
  private final SpymemcachedRequestAssociations associations;
  private final boolean retry;
  private final Map<SpymemcachedRequest, RetryState> retries;

  private SpymemcachedRequestHolder(SpymemcachedRequest request) {
    this(SpymemcachedRequestAssociations.create(request), false);
  }

  private SpymemcachedRequestHolder(SpymemcachedRequestAssociations associations, boolean retry) {
    this.associations = associations;
    this.retry = retry;
    this.retries = retry ? new IdentityHashMap<>() : emptyMap();
  }

  public static Context init(Context context, SpymemcachedRequest request) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new SpymemcachedRequestHolder(request));
  }

  public static void associateOperation(Context context, Operation operation) {
    SpymemcachedRequestHolder holder = context.get(KEY);
    if (holder != null) {
      REQUESTS.set(operation, holder.associations.forOperation(operation));
    }
  }

  public static void propagateOperation(Operation target, Operation source) {
    SpymemcachedRequestAssociations sourceAssociations = REQUESTS.get(source);
    if (sourceAssociations == null) {
      return;
    }
    SpymemcachedRequestAssociations targetAssociations = REQUESTS.get(target);
    if (targetAssociations == null) {
      targetAssociations = SpymemcachedRequestAssociations.create();
      REQUESTS.set(target, targetAssociations);
    }
    targetAssociations.merge(sourceAssociations);
  }

  public static void captureHandlingNode(Context context, Operation operation) {
    SpymemcachedRequestHolder holder = context.get(KEY);
    if (holder == null) {
      return;
    }
    SpymemcachedRequestAssociations operationAssociations = REQUESTS.get(operation);
    if (operationAssociations == null) {
      return;
    }
    MemcachedNode node = operation.getHandlingNode();
    for (SpymemcachedRequest request : operationAssociations.requests()) {
      Collection<String> operationKeys = operationAssociations.getRequestKeys(request);
      if (!holder.retry) {
        request.setHandlingNode(node, operationKeys);
      } else {
        RetryState retryState = holder.retries.get(request);
        if (retryState == null) {
          retryState = new RetryState();
          holder.retries.put(request, retryState);
        }
        retryState.captureHandlingNode(node, operationKeys);
      }
    }
  }

  @Nullable
  public static RetryScope startRetry(Operation operation) {
    SpymemcachedRequestAssociations associations = REQUESTS.get(operation);
    if (associations == null) {
      return null;
    }
    SpymemcachedRequestHolder holder = new SpymemcachedRequestHolder(associations, true);
    return new RetryScope(holder, Context.current().with(holder).makeCurrent());
  }

  private void completeRetry() {
    for (Map.Entry<SpymemcachedRequest, RetryState> entry : retries.entrySet()) {
      RetryState retry = entry.getValue();
      if (retry.hasMultipleHandlingNodes
          || (retry.keys.isEmpty()
              && associations.hasRequestKeysOutside(entry.getKey(), retry.keys))) {
        entry.getKey().clearHandlingNode();
      } else if (retry.handlingNode != null) {
        entry.getKey().setRetryHandlingNode(retry.handlingNode);
      }
    }
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  private static class RetryState {
    @Nullable private MemcachedNode handlingNode;
    private final Set<String> keys = new HashSet<>();
    private boolean hasMultipleHandlingNodes;

    void captureHandlingNode(@Nullable MemcachedNode node, Collection<String> operationKeys) {
      if (node == null || hasMultipleHandlingNodes) {
        return;
      }
      keys.addAll(operationKeys);
      if (handlingNode != null && node != handlingNode) {
        handlingNode = null;
        hasMultipleHandlingNodes = true;
        return;
      }
      handlingNode = node;
    }
  }

  public static class RetryScope implements AutoCloseable {
    private final SpymemcachedRequestHolder holder;
    private final Scope scope;

    private RetryScope(SpymemcachedRequestHolder holder, Scope scope) {
      this.holder = holder;
      this.scope = scope;
    }

    @Override
    public void close() {
      try {
        holder.completeRetry();
      } finally {
        scope.close();
      }
    }
  }
}
