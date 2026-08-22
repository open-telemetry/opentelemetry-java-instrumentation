/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;

class GeodeServerAddress {

  private static final GeodeServerAddress NONE = new GeodeServerAddress(null, null);

  @Nullable private final String address;
  @Nullable private final Integer port;

  static GeodeServerAddress get(Region<?, ?> region) {
    Pool pool = PoolManager.find(region);
    if (pool == null) {
      return NONE;
    }
    List<InetSocketAddress> servers = pool.getServers();
    if (servers.size() != 1) {
      return NONE;
    }
    InetSocketAddress server = servers.get(0);
    return new GeodeServerAddress(server.getHostString(), server.getPort());
  }

  private GeodeServerAddress(@Nullable String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  String address() {
    return address;
  }

  @Nullable
  Integer port() {
    return port;
  }
}
