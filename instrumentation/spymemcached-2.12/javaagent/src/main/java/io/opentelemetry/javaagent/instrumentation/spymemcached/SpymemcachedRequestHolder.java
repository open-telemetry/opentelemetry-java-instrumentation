/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.spy.memcached.MemcachedNode;

public final class SpymemcachedRequestHolder implements ImplicitContextKeyed {

  private static final ContextKey<SpymemcachedRequestHolder> KEY =
      named("opentelemetry-spymemcached-request-holder");

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

  public static void setHandlingNode(Context context, MemcachedNode node) {
    if (node == null) {
      return;
    }

    SocketAddress socketAddress = node.getSocketAddress();
    if (!(socketAddress instanceof InetSocketAddress)) {
      return;
    }

    SpymemcachedRequestHolder holder = context.get(KEY);
    if (holder != null) {
      holder.request.setHandlingNode(node);
    }
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
