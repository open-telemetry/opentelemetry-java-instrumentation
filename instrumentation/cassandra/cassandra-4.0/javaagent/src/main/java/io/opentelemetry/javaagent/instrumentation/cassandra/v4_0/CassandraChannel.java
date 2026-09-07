/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public class CassandraChannel {

  private static final MethodType ACCESSOR_TYPE = MethodType.methodType(Object.class, Object.class);

  private static final ClassValue<MethodHandle> channelMethods =
      new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
          return createAccessor(type, "channel");
        }
      };

  private static final ClassValue<MethodHandle> remoteAddressMethods =
      new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
          return createAccessor(type, "remoteAddress");
        }
      };

  @Nullable
  public static InetSocketAddress getRemoteAddress(Object context) {
    try {
      Object channel = (Object) channelMethods.get(context.getClass()).invokeExact(context);
      Object remoteAddress =
          (Object) remoteAddressMethods.get(channel.getClass()).invokeExact(channel);
      if (!(remoteAddress instanceof InetSocketAddress)
          || ((InetSocketAddress) remoteAddress).isUnresolved()) {
        return null;
      }
      return (InetSocketAddress) remoteAddress;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static MethodHandle createAccessor(Class<?> type, String name) {
    Method method = findPublicInterfaceMethod(type, name);
    if (method == null) {
      throw new IllegalStateException(
          "No public " + name + "() interface method on " + type.getName());
    }
    try {
      return MethodHandles.publicLookup().unreflect(method).asType(ACCESSOR_TYPE);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot access " + method, e);
    }
  }

  @Nullable
  private static Method findPublicInterfaceMethod(Class<?> type, String name) {
    for (Class<?> interfaceType : type.getInterfaces()) {
      if (Modifier.isPublic(interfaceType.getModifiers())) {
        try {
          return interfaceType.getMethod(name);
        } catch (NoSuchMethodException ignored) {
          // Continue with the other public interfaces.
        }
      }
      Method method = findPublicInterfaceMethod(interfaceType, name);
      if (method != null) {
        return method;
      }
    }
    Class<?> superclass = type.getSuperclass();
    if (superclass != null) {
      return findPublicInterfaceMethod(superclass, name);
    }
    return null;
  }

  private CassandraChannel() {}
}
