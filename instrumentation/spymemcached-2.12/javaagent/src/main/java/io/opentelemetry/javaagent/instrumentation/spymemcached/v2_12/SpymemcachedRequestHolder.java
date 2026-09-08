/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import net.spy.memcached.ops.Operation;

public class SpymemcachedRequestHolder implements ImplicitContextKeyed {

  private static final ContextKey<SpymemcachedRequestHolder> KEY =
      named("opentelemetry-spymemcached-request-holder");
  private static final VirtualField<Operation, SpymemcachedRequestAssociations> REQUESTS =
      VirtualField.find(Operation.class, SpymemcachedRequestAssociations.class);

  private final SpymemcachedRequest request;

  private SpymemcachedRequestHolder(SpymemcachedRequest request) {
    this.request = request;
  }

  public static Context init(Context context, SpymemcachedRequest request) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new SpymemcachedRequestHolder(request));
  }

  public static void associateOperation(Context context, Operation operation) {
    SpymemcachedRequestHolder holder = context.get(KEY);
    if (holder == null) {
      return;
    }
    SpymemcachedRequestAssociations associations = REQUESTS.get(operation);
    if (associations == null) {
      associations = new SpymemcachedRequestAssociations();
      REQUESTS.set(operation, associations);
    }
    associations.add(holder.request);
  }

  public static void propagateOperation(Operation target, Operation source) {
    SpymemcachedRequestAssociations sourceAssociations = REQUESTS.get(source);
    if (sourceAssociations == null) {
      return;
    }
    SpymemcachedRequestAssociations targetAssociations = REQUESTS.get(target);
    if (targetAssociations == null) {
      targetAssociations = new SpymemcachedRequestAssociations();
      REQUESTS.set(target, targetAssociations);
    }
    targetAssociations.merge(sourceAssociations);
  }

  public static void captureHandlingNode(Context context, Operation operation) {
    SpymemcachedRequestHolder holder = context.get(KEY);
    if (holder == null) {
      return;
    }
    holder.request.setHandlingNode(operation.getHandlingNode());
  }

  public static void markRedistributed(Operation operation) {
    SpymemcachedRequestAssociations associations = REQUESTS.get(operation);
    if (associations == null) {
      return;
    }
    for (SpymemcachedRequest request : associations.requests()) {
      request.markRedistributed();
    }
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
