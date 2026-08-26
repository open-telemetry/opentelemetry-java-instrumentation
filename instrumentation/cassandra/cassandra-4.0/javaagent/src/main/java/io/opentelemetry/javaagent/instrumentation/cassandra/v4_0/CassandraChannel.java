/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public final class CassandraChannel {

  private static final ClassValue<Method> channelMethods =
      new ClassValue<Method>() {
        @Override
        protected Method computeValue(Class<?> type) {
          return findPublicInterfaceMethod(type, "channel");
        }
      };

  private static final ClassValue<Method> remoteAddressMethods =
      new ClassValue<Method>() {
        @Override
        protected Method computeValue(Class<?> type) {
          return findPublicInterfaceMethod(type, "remoteAddress");
        }
      };

  @Nullable
  public static InetSocketAddress getRemoteAddress(Object context)
      throws ReflectiveOperationException {
    Object channel = channelMethods.get(context.getClass()).invoke(context);
    Object remoteAddress = remoteAddressMethods.get(channel.getClass()).invoke(channel);
    if (!(remoteAddress instanceof InetSocketAddress)
        || ((InetSocketAddress) remoteAddress).isUnresolved()) {
      return null;
    }
    return (InetSocketAddress) remoteAddress;
  }

  private static Method findPublicInterfaceMethod(Class<?> type, String name) {
    for (Class<?> interfaceType : type.getInterfaces()) {
      if (Modifier.isPublic(interfaceType.getModifiers())) {
        try {
          return interfaceType.getMethod(name);
        } catch (NoSuchMethodException ignored) {
          // Continue with the other public interfaces.
        }
      }
      try {
        return findPublicInterfaceMethod(interfaceType, name);
      } catch (IllegalStateException ignored) {
        // Continue with the other interfaces.
      }
    }
    Class<?> superclass = type.getSuperclass();
    if (superclass != null) {
      return findPublicInterfaceMethod(superclass, name);
    }
    throw new IllegalStateException(
        "No public " + name + "() interface method on " + type.getName());
  }

  private CassandraChannel() {}
}
