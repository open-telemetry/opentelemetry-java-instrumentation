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
    try {
      Method method = getSocketHostAndPortMethod;
      if (method == null) {
        Class<?> declaringClass = findDefaultJedisSocketFactoryClass(socketFactory.getClass());
        if (declaringClass == null) {
          return null;
        }
        method = declaringClass.getDeclaredMethod("getSocketHostAndPort");
        method.setAccessible(true);
        getSocketHostAndPortMethod = method;
      } else if (!method.getDeclaringClass().isInstance(socketFactory)) {
        return null;
      }
      return (HostAndPort) method.invoke(socketFactory);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> findDefaultJedisSocketFactoryClass(Class<?> socketFactoryClass) {
    for (Class<?> current = socketFactoryClass;
        current != null;
        current = current.getSuperclass()) {
      if (DEFAULT_JEDIS_SOCKET_FACTORY.equals(current.getName())) {
        return current;
      }
    }
    return null;
  }

  private DefaultJedisSocketFactoryAccess() {}
}
