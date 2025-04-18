/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

@SuppressWarnings("all")
public class SocketAccessor {

  private static final String LAYERED_TRANSPORT =
      "org.apache.thrift.transport.layered.TLayeredTransport";
  private static final String FRAMED_TRANSPORT = "org.apache.thrift.transport.TFramedTransport";
  private static final String FAST_FRAMED_TRANSPORT =
      "org.apache.thrift.transport.TFastFramedTransport";

  private SocketAccessor() {}

  public static Socket getSocket(TTransport transport) {
    if (transport == null) {
      return null;
    }
    try {
      if (transport instanceof TSocket) {
        return ((TSocket) transport).getSocket();
      }
      if (transport instanceof TNonblockingSocket) {
        return ((TNonblockingSocket) transport).getSocketChannel().socket();
      }
      if (transport instanceof TSaslClientTransport) {
        return getSocket(((TSaslClientTransport) transport).getUnderlyingTransport());
      }
      Class<?> thisClass = transport.getClass();
      Class<?> superClass = thisClass.getSuperclass();
      Class<?> layeredTransport = getClass(LAYERED_TRANSPORT);
      if (superClass != null && superClass == layeredTransport) {
        Method parentMethod = superClass.getMethod("getInnerTransport");
        Object result = parentMethod.invoke(transport);
        if (result != null && result instanceof TTransport) {
          return getSocket((TTransport) result);
        }
      }

      if (thisClass == getClass(FRAMED_TRANSPORT)) {
        return getInnerTransportSocket(thisClass, "transport_", transport);
      }
      if (thisClass == getClass(FAST_FRAMED_TRANSPORT)) {
        return getInnerTransportSocket(thisClass, "underlying", transport);
      }
    } catch (Throwable e) {
      return null;
    }
    return null;
  }

  public static Class<?> getClass(String className) {
    try {
      return Class.forName(className);
    } catch (Throwable e) {
      return null;
    }
  }

  private static Socket getInnerTransportSocket(
      Class<?> thisClass, String targetField, TTransport transport)
      throws NoSuchFieldException, IllegalAccessException {
    Field field = thisClass.getDeclaredField(targetField);
    field.setAccessible(true);
    Object fieldTransport = field.get(transport);
    if (fieldTransport instanceof TTransport) {
      return getSocket((TTransport) fieldTransport);
    }
    return null;
  }
}
