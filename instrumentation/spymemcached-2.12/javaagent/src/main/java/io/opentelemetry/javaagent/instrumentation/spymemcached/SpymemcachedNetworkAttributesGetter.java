/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;

final class SpymemcachedNetworkAttributesGetter
    implements ServerAttributesGetter<SpymemcachedRequest> {

  @Nullable
  @Override
  public String getServerAddress(SpymemcachedRequest request) {
    MemcachedConnection connection = request.getConnection();
    if (connection != null) {
      Collection<MemcachedNode> nodes = connection.getLocator().getAll();
      if (!nodes.isEmpty()) {
        SocketAddress socketAddress = nodes.iterator().next().getSocketAddress();
        if (socketAddress instanceof InetSocketAddress) {
          return ((InetSocketAddress) socketAddress).getHostString();
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(SpymemcachedRequest request) {
    MemcachedConnection connection = request.getConnection();
    if (connection != null) {
      Collection<MemcachedNode> nodes = connection.getLocator().getAll();
      if (!nodes.isEmpty()) {
        SocketAddress socketAddress = nodes.iterator().next().getSocketAddress();
        if (socketAddress instanceof InetSocketAddress) {
          return ((InetSocketAddress) socketAddress).getPort();
        }
      }
    }
    return null;
  }
}