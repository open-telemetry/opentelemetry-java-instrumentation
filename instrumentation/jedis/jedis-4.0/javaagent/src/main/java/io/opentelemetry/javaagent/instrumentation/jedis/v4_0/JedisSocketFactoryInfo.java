/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisSocketFactory;

public final class JedisSocketFactoryInfo {
  private static final VirtualField<JedisSocketFactory, JedisSocketFactoryInfo>
      SOCKET_FACTORY_INFO =
          VirtualField.find(JedisSocketFactory.class, JedisSocketFactoryInfo.class);

  private final HostAndPort configuredHostAndPort;

  private JedisSocketFactoryInfo(HostAndPort configuredHostAndPort) {
    this.configuredHostAndPort = configuredHostAndPort;
  }

  @Nullable
  public static HostAndPort getConfiguredHostAndPort(@Nullable JedisSocketFactory socketFactory) {
    JedisSocketFactoryInfo info =
        socketFactory == null ? null : SOCKET_FACTORY_INFO.get(socketFactory);
    return info == null ? null : info.configuredHostAndPort;
  }

  public static void setConfiguredHostAndPort(
      JedisSocketFactory socketFactory, HostAndPort hostAndPort) {
    SOCKET_FACTORY_INFO.set(socketFactory, new JedisSocketFactoryInfo(hostAndPort));
  }
}
