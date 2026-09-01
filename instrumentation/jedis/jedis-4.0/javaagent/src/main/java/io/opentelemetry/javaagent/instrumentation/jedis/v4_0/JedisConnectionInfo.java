/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import javax.annotation.Nullable;
import redis.clients.jedis.DefaultJedisSocketFactoryUtil;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisSocketFactory;

class JedisConnectionInfo {
  @Nullable private final String serverAddress;
  @Nullable private final Integer serverPort;
  @Nullable private final String configuredAddress;
  @Nullable private final Integer configuredPort;
  @Nullable private final Long databaseIndex;

  private JedisConnectionInfo(
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      @Nullable String configuredAddress,
      @Nullable Integer configuredPort,
      @Nullable Long databaseIndex) {
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
    this.configuredAddress = configuredAddress;
    this.configuredPort = configuredPort;
    this.databaseIndex = databaseIndex;
  }

  static JedisConnectionInfo create(
      @Nullable JedisSocketFactory socketFactory, @Nullable Object clientConfig) {
    // The socket endpoint is the one the connection dials, after any HostAndPortMapper ran.
    HostAndPort socketHostAndPort =
        DefaultJedisSocketFactoryUtil.getSocketHostAndPort(socketFactory);
    // The configured endpoint is the one the client was given, before any mapping.
    HostAndPort configuredHostAndPort =
        JedisSocketFactoryInfo.getConfiguredHostAndPort(socketFactory);
    if (configuredHostAndPort == null) {
      configuredHostAndPort = socketHostAndPort;
    }
    // Without a client config, Jedis leaves the new Redis connection on the default database 0.
    Long databaseIndex =
        clientConfig instanceof JedisClientConfig
            ? Long.valueOf(((JedisClientConfig) clientConfig).getDatabase())
            : 0L;
    return new JedisConnectionInfo(
        socketHostAndPort != null ? socketHostAndPort.getHost() : null,
        socketHostAndPort != null ? socketHostAndPort.getPort() : null,
        configuredHostAndPort != null ? configuredHostAndPort.getHost() : null,
        configuredHostAndPort != null ? configuredHostAndPort.getPort() : null,
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

  @Nullable
  RedisServerTarget getServerTarget() {
    return configuredAddress == null || configuredPort == null
        ? null
        : RedisServerTarget.ofHostAndPort(configuredAddress, configuredPort);
  }
}
