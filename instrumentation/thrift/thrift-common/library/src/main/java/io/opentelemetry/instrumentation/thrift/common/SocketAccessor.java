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

public class SocketAccessor {
  private static final String TRANSPORT = "transport_";
  private static final String UNDERLYING = "underlying";
  private static final String GET_INNER_TRANSPORT = "getInnerTransport";

  private static final Class<?> LAYERED_TRANSPORT =
      getClass("org.apache.thrift.transport.layered.TLayeredTransport");
  private static final Class<?> FRAMED_TRANSPORT =
      getClass("org.apache.thrift.transport.TFramedTransport");
  private static final Class<?> FAST_FRAMED_TRANSPORT =
      getClass("org.apache.thrift.transport.TFastFramedTransport");

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
      Class<?> layeredTransport = LAYERED_TRANSPORT;
      if (superClass != null && superClass == layeredTransport) {
        Method parentMethod = superClass.getMethod(GET_INNER_TRANSPORT);
        Object result = parentMethod.invoke(transport);
        if (result != null && result instanceof TTransport) {
          return getSocket((TTransport) result);
        }
      }

      if (thisClass == FRAMED_TRANSPORT) {
        return getInnerTransportSocket(thisClass, TRANSPORT, transport);
      }
      if (thisClass == FAST_FRAMED_TRANSPORT) {
        return getInnerTransportSocket(thisClass, UNDERLYING, transport);
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
