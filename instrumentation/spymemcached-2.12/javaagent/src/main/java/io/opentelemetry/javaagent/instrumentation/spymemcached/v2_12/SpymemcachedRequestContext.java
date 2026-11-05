/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;

public class SpymemcachedRequestContext implements ImplicitContextKeyed {

  private static final ContextKey<SpymemcachedRequestContext> KEY =
      named("opentelemetry-spymemcached-request-context");
  private static final VirtualField<Operation, SpymemcachedRequestSet> REQUESTS =
      VirtualField.find(Operation.class, SpymemcachedRequestSet.class);

  private final SpymemcachedRequest request;
  private final boolean retry;

  private SpymemcachedRequestContext(SpymemcachedRequest request, boolean retry) {
    this.request = request;
    this.retry = retry;
  }

  public static Context init(Context context, SpymemcachedRequest request) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new SpymemcachedRequestContext(request, false));
  }

  public static void trackOperation(Context context, Operation operation) {
    SpymemcachedRequestContext requestContext = context.get(KEY);
    if (requestContext == null) {
      return;
    }
    if (requestContext.retry && !isSingleKeyOperation(operation)) {
      requestContext.request.suppressHandlingNodeAddress();
    }
    SpymemcachedRequestSet requestSet = REQUESTS.get(operation);
    if (requestSet == null) {
      requestSet = new SpymemcachedRequestSet();
      REQUESTS.set(operation, requestSet);
    }
    requestSet.add(requestContext.request);
  }

  public static void propagateOperation(Operation target, Operation source) {
    SpymemcachedRequestSet sourceRequestSet = REQUESTS.get(source);
    if (sourceRequestSet == null) {
      return;
    }
    SpymemcachedRequestSet targetRequestSet = REQUESTS.get(target);
    if (targetRequestSet == null) {
      targetRequestSet = new SpymemcachedRequestSet();
      REQUESTS.set(target, targetRequestSet);
    }
    targetRequestSet.merge(sourceRequestSet);
  }

  public static void captureHandlingNode(Context context, Operation operation, MemcachedNode node) {
    SpymemcachedRequestContext requestContext = context.get(KEY);
    SpymemcachedRequestSet requestSet = REQUESTS.get(operation);
    if (requestContext == null
        || requestSet == null
        || requestSet.getSingleRequest() != requestContext.request) {
      return;
    }
    if (requestContext.retry) {
      requestContext.request.setRetryHandlingNode(node);
    } else {
      requestContext.request.setHandlingNode(node);
    }
  }

  @Nullable
  public static Scope startRetry(Operation operation) {
    SpymemcachedRequestSet requestSet = REQUESTS.get(operation);
    if (requestSet == null || operation.isCancelled() || operation.isTimedOut()) {
      return null;
    }
    SpymemcachedRequest request = requestSet.getSingleRequest();
    if (request != null
        && !request.getOperationName().equals("getBulk")
        && isSingleKeyOperation(operation)) {
      // Bind cloned operations while redistributeOperation enqueues them, before publication.
      return Context.current().with(new SpymemcachedRequestContext(request, true)).makeCurrent();
    }
    for (SpymemcachedRequest trackedRequest : requestSet.requests()) {
      trackedRequest.suppressHandlingNodeAddress();
    }
    return null;
  }

  private static boolean isSingleKeyOperation(Operation operation) {
    return operation instanceof KeyedOperation
        && ((KeyedOperation) operation).getKeys().size() == 1;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
