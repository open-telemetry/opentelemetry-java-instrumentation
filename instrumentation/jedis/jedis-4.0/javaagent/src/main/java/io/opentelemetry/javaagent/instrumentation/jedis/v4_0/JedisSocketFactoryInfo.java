/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisSocketFactory;

public class JedisSocketFactoryInfo {
  private static final VirtualField<JedisSocketFactory, HostAndPort> CONFIGURED_HOST_AND_PORT =
      VirtualField.find(JedisSocketFactory.class, HostAndPort.class);

  @Nullable
  public static HostAndPort getConfiguredHostAndPort(@Nullable JedisSocketFactory socketFactory) {
    return socketFactory == null ? null : CONFIGURED_HOST_AND_PORT.get(socketFactory);
  }

  public static void setConfiguredHostAndPort(
      JedisSocketFactory socketFactory, HostAndPort hostAndPort) {
    CONFIGURED_HOST_AND_PORT.set(socketFactory, hostAndPort);
  }

  private JedisSocketFactoryInfo() {}
}
