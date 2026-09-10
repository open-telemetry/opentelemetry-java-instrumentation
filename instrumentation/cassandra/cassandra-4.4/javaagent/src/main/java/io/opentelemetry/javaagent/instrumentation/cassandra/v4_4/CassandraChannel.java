/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * Reads remote addresses from both regular and driver-shaded Netty objects.
 *
 * <p>The shaded Cassandra driver relocates Netty classes, so this code cannot refer to {@code
 * ChannelHandlerContext} or {@code Channel} by type.
 */
public class CassandraChannel {

  private static final MethodType ACCESSOR_TYPE = MethodType.methodType(Object.class, Object.class);
  private static final MethodHandle MISSING_ACCESSOR =
      MethodHandles.dropArguments(MethodHandles.constant(Object.class, null), 0, Object.class);

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
      if (channel == null) {
        return null;
      }
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
    // A public method declared by a package-private implementation is not accessible through
    // publicLookup(). Resolve the method from a public interface to get an accessible declaring
    // type.
    Method method = findPublicInterfaceMethod(type, name);
    if (method == null) {
      return MISSING_ACCESSOR;
    }
    try {
      return MethodHandles.publicLookup().unreflect(method).asType(ACCESSOR_TYPE);
    } catch (IllegalAccessException ignored) {
      return MISSING_ACCESSOR;
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
      } else {
        Method method = findPublicInterfaceMethod(interfaceType, name);
        if (method != null) {
          return method;
        }
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
