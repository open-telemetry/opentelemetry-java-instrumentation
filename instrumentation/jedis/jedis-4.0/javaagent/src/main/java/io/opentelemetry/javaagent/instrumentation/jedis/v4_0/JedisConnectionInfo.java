/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import javax.annotation.Nullable;
import redis.clients.jedis.DefaultJedisSocketFactoryUtil;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisSocketFactory;

class JedisConnectionInfo {
  @Nullable private final String serverAddress;
  @Nullable private final Integer serverPort;
  @Nullable private final Long databaseIndex;

  private JedisConnectionInfo(
      @Nullable String serverAddress, @Nullable Integer serverPort, @Nullable Long databaseIndex) {
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
    this.databaseIndex = databaseIndex;
  }

  static JedisConnectionInfo create(
      @Nullable JedisSocketFactory socketFactory, @Nullable Object clientConfig) {
    HostAndPort hostAndPort = DefaultJedisSocketFactoryUtil.getHostAndPort(socketFactory);
    Long databaseIndex =
        clientConfig instanceof JedisClientConfig
            ? ((JedisClientConfig) clientConfig).getDatabase()
            : null;
    return new JedisConnectionInfo(
        hostAndPort != null ? hostAndPort.getHost() : null,
        hostAndPort != null ? hostAndPort.getPort() : null,
        databaseIndex);
  }

  @Nullable
  String getServerAddress() {
    return serverAddress;
  }

  @Nullable
  Integer getServerPort() {
    return serverPort;
  }

  @Nullable
  Long getDatabaseIndex() {
    return databaseIndex;
  }
}
