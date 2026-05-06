/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SocketAccessor {
  @Nullable
  private static final Class<?> LAYERED_TRANSPORT =
      getClass("org.apache.thrift.transport.layered.TLayeredTransport");

  @Nullable
  private static final Class<?> FRAMED_TRANSPORT =
      getClass("org.apache.thrift.transport.TFramedTransport");

  @Nullable
  private static final Class<?> FAST_FRAMED_TRANSPORT =
      getClass("org.apache.thrift.transport.TFastFramedTransport");

  @Nullable
  private static final Field framedTransportField = getField(FRAMED_TRANSPORT, "transport_");

  @Nullable
  private static final Field fastFramedTransportField =
      getField(FAST_FRAMED_TRANSPORT, "underlying");

  @Nullable
  private static final Method layeredTransportMethod =
      LAYERED_TRANSPORT != null ? getMethod(LAYERED_TRANSPORT, "getInnerTransport") : null;

  @Nullable
  private static final Field asyncClientTransportField =
      getField(TAsyncClient.class, "___transport");

  @Nullable
  private static final Field socketAddressField =
      getField(TNonblockingSocket.class, "socketAddress_");

  private SocketAccessor() {}

  @Nullable
  public static SocketAddress getSocketAddress(TAsyncClient asyncClient) {
    if (asyncClientTransportField == null || socketAddressField == null) {
      return null;
    }
    Object fieldTransport;
    try {
      fieldTransport = asyncClientTransportField.get(asyncClient);
    } catch (IllegalAccessException e) {
      return null;
    }
    if (!(fieldTransport instanceof TNonblockingSocket)) {
      return null;
    }
    try {
      return (SocketAddress) socketAddressField.get(fieldTransport);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  @Nullable
  public static Socket getSocket(@Nullable TTransport transport) {
    if (transport == null) {
      return null;
    }
    if (transport instanceof TSocket) {
      return ((TSocket) transport).getSocket();
    }
    if (transport instanceof TNonblockingSocket) {
      return ((TNonblockingSocket) transport).getSocketChannel().socket();
    }
    if (transport instanceof TSaslClientTransport) {
      return getSocket(((TSaslClientTransport) transport).getUnderlyingTransport());
    }
    if (FRAMED_TRANSPORT != null && FRAMED_TRANSPORT.isInstance(transport)) {
      return getInnerTransportSocket(framedTransportField, transport);
    }
    if (FAST_FRAMED_TRANSPORT != null && FAST_FRAMED_TRANSPORT.isInstance(transport)) {
      return getInnerTransportSocket(fastFramedTransportField, transport);
    }
    if (LAYERED_TRANSPORT != null
        && LAYERED_TRANSPORT.isInstance(transport)
        && layeredTransportMethod != null) {
      Object result;
      try {
        result = layeredTransportMethod.invoke(transport);
      } catch (IllegalAccessException | InvocationTargetException e) {
        return null;
      }
      if (result instanceof TTransport) {
        return getSocket((TTransport) result);
      }
    }
    return null;
  }

  @Nullable
  public static Class<?> getClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  @Nullable
  private static Socket getInnerTransportSocket(@Nullable Field field, TTransport transport) {
    if (field == null) {
      return null;
    }
    Object fieldTransport;
    try {
      fieldTransport = field.get(transport);
    } catch (IllegalAccessException e) {
      return null;
    }
    if (fieldTransport instanceof TTransport) {
      return getSocket((TTransport) fieldTransport);
    }
    return null;
  }

  @Nullable
  private static Field getField(@Nullable Class<?> clazz, String fieldName) {
    if (clazz == null) {
      return null;
    }
    try {
      Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException | SecurityException e) {
      return null;
    }
  }

  @Nullable
  private static Method getMethod(Class<?> clazz, String methodName) {
    try {
      return clazz.getMethod(methodName);
    } catch (NoSuchMethodException | SecurityException e) {
      return null;
    }
  }
}
