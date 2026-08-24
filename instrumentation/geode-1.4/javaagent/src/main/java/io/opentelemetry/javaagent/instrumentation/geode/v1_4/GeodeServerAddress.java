/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;

class GeodeServerAddress {

  private static final GeodeServerAddress NONE = new GeodeServerAddress(null, null);
  private static final VirtualField<Region<?, ?>, GeodeServerAddress> SERVER_ADDRESSES =
      VirtualField.find(Region.class, GeodeServerAddress.class);

  @Nullable private final String address;
  @Nullable private final Integer port;

  static GeodeServerAddress get(Region<?, ?> region) {
    GeodeServerAddress serverAddress = SERVER_ADDRESSES.get(region);
    if (serverAddress == null) {
      serverAddress = resolve(region);
      SERVER_ADDRESSES.set(region, serverAddress);
    }
    return serverAddress;
  }

  private static GeodeServerAddress resolve(Region<?, ?> region) {
    Pool pool = PoolManager.find(region);
    if (pool == null) {
      return NONE;
    }
    // Pool.getServers() reports the servers that the pool was configured with. That list is fixed
    // for the lifetime of the pool, and servers that the pool discovers through locators are not
    // part of it, so the result is stable for a given region.
    //
    // On Geode 2.x this call re-creates each InetSocketAddress from its host name, which resolves
    // the name, so it must not run on every operation.
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
