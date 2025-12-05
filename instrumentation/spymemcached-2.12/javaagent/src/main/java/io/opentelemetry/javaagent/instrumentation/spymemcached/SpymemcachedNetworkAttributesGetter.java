/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedNode;

final class SpymemcachedNetworkAttributesGetter
    implements ServerAttributesGetter<SpymemcachedRequest>,
        NetworkAttributesGetter<SpymemcachedRequest, Object> {

  @Nullable
  @Override
  public String getServerAddress(SpymemcachedRequest request) {
    MemcachedNode handlingNode = request.getHandlingNode();
    if (handlingNode != null) {
      SocketAddress socketAddress = handlingNode.getSocketAddress();
      if (socketAddress instanceof InetSocketAddress) {
        return ((InetSocketAddress) socketAddress).getHostString();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(SpymemcachedRequest request) {
    MemcachedNode handlingNode = request.getHandlingNode();
    if (handlingNode != null) {
      SocketAddress socketAddress = handlingNode.getSocketAddress();
      if (socketAddress instanceof InetSocketAddress) {
        return ((InetSocketAddress) socketAddress).getPort();
      }
    }
    return null;
  }
}
