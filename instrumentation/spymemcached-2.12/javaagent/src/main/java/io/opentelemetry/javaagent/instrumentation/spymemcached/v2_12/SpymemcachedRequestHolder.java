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
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;

public class SpymemcachedRequestHolder implements ImplicitContextKeyed {

  private static final ContextKey<SpymemcachedRequestHolder> KEY =
      named("opentelemetry-spymemcached-request-holder");
  private static final VirtualField<Operation, SpymemcachedRequestAssociations> REQUESTS =
      VirtualField.find(Operation.class, SpymemcachedRequestAssociations.class);

  private final SpymemcachedRequestAssociations associations;
  private final boolean retry;
  private final Map<SpymemcachedRequest, MemcachedNode> retryNodes;

  private SpymemcachedRequestHolder(SpymemcachedRequest request) {
    this(SpymemcachedRequestAssociations.create(request), false);
  }

  private SpymemcachedRequestHolder(SpymemcachedRequestAssociations associations, boolean retry) {
    this.associations = associations;
    this.retry = retry;
    // SpymemcachedRequest is an AutoValue type with value equality, so retried requests have to be
    // told apart by identity
    this.retryNodes = retry ? new IdentityHashMap<>() : emptyMap();
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
      if (!holder.retry) {
        request.setHandlingNode(node);
      } else if (operation instanceof KeyedOperation
          && holder.associations.hasRequestKeysOutside(
              request, ((KeyedOperation) operation).getKeys())) {
        request.clearHandlingNode();
      } else if (!holder.retryNodes.containsKey(request)) {
        holder.retryNodes.put(request, node);
        request.setRetryHandlingNode(node);
      } else if (node != holder.retryNodes.get(request)) {
        request.clearHandlingNode();
      }
    }
  }

  @Nullable
  public static RetryScope startRetry(Operation operation) {
    SpymemcachedRequestAssociations associations = REQUESTS.get(operation);
    if (associations == null) {
      return null;
    }
    return new RetryScope(
        Context.current().with(new SpymemcachedRequestHolder(associations, true)).makeCurrent());
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  public static class RetryScope implements AutoCloseable {
    private final Scope scope;

    private RetryScope(Scope scope) {
      this.scope = scope;
    }

    @Override
    public void close() {
      scope.close();
    }
  }
}
