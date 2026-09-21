/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MongoNetworkPeer {

  private final String address;
  @Nullable private final Integer port;
  @Nullable private final InetSocketAddress inetSocketAddress;

  @Nullable
  public static MongoNetworkPeer fromSocketAddress(@Nullable SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
      if (inetSocketAddress.isUnresolved() || inetSocketAddress.getAddress() == null) {
        return null;
      }
      return new MongoNetworkPeer(
          inetSocketAddress.getAddress().getHostAddress(),
          inetSocketAddress.getPort(),
          inetSocketAddress);
    }

    String path = getUnixSocketPath(socketAddress);
    return path == null || path.isEmpty() ? null : new MongoNetworkPeer(path, null, null);
  }

  private MongoNetworkPeer(
      String address, @Nullable Integer port, @Nullable InetSocketAddress inetSocketAddress) {
    this.address = address;
    this.port = port;
    this.inetSocketAddress = inetSocketAddress;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  @Nullable
  public InetSocketAddress getInetSocketAddress() {
    return inetSocketAddress;
  }

  @Nullable
  private static String getUnixSocketPath(@Nullable SocketAddress socketAddress) {
    if (socketAddress == null) {
      return null;
    }

    Object path = invokeNoArg(socketAddress, "getPath");
    if (path == null) {
      path = invokeNoArg(socketAddress, "path");
    }
    return path == null ? null : path.toString();
  }

  @Nullable
  private static Object invokeNoArg(SocketAddress socketAddress, String methodName) {
    try {
      Method method = socketAddress.getClass().getMethod(methodName);
      return method.invoke(socketAddress);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
      return null;
    }
  }
}
