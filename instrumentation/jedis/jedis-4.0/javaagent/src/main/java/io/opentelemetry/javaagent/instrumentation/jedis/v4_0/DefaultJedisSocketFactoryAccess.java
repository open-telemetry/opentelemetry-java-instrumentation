/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import java.lang.reflect.Method;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisSocketFactory;

final class DefaultJedisSocketFactoryAccess {

  private static final String DEFAULT_JEDIS_SOCKET_FACTORY =
      "redis.clients.jedis.DefaultJedisSocketFactory";

  @Nullable private static volatile Method getSocketHostAndPortMethod;

  @Nullable
  static HostAndPort getHostAndPort(@Nullable JedisSocketFactory socketFactory) {
    if (socketFactory == null) {
      return null;
    }
    if (!DEFAULT_JEDIS_SOCKET_FACTORY.equals(socketFactory.getClass().getName())) {
      return null;
    }
    try {
      Method method = getSocketHostAndPortMethod;
      if (method == null) {
        method = socketFactory.getClass().getDeclaredMethod("getSocketHostAndPort");
        method.setAccessible(true);
        getSocketHostAndPortMethod = method;
      }
      return (HostAndPort) method.invoke(socketFactory);
    } catch (Throwable e) {
      return null;
    }
  }

  private DefaultJedisSocketFactoryAccess() {}
}
