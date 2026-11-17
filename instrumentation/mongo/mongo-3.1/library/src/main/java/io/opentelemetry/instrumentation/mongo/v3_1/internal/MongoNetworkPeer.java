/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.semconv.network.internal.SocketAddressUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.SocketAddress;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MongoNetworkPeer {

  private static final Logger logger = Logger.getLogger(MongoNetworkPeer.class.getName());
  // Resolve optional socket types through their runtime class, not the helper's class loader.
  private static final ClassValue<MethodHandle> jnrSocketPaths =
      new ClassValue<MethodHandle>() {
        @Override
        @Nullable
        protected MethodHandle computeValue(Class<?> type) {
          if (!type.getName().equals("jnr.unixsocket.UnixSocketAddress")) {
            return null;
          }
          try {
            return MethodHandles.publicLookup()
                .findVirtual(type, "path", MethodType.methodType(String.class));
          } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
          }
        }
      };

  private final String address;
  @Nullable private final Integer port;
  private final SocketAddress socketAddress;

  @Nullable
  public static MongoNetworkPeer fromSocketAddress(@Nullable SocketAddress socketAddress) {
    if (socketAddress == null) {
      return null;
    }
    String address = SocketAddressUtil.getAddress(socketAddress);
    if (address == null) {
      address = getJnrSocketPath(socketAddress);
    }
    return address == null || address.isEmpty()
        ? null
        : new MongoNetworkPeer(address, SocketAddressUtil.getPort(socketAddress), socketAddress);
  }

  private MongoNetworkPeer(String address, @Nullable Integer port, SocketAddress socketAddress) {
    this.address = address;
    this.port = port;
    this.socketAddress = socketAddress;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  @Nullable
  private static String getJnrSocketPath(SocketAddress socketAddress) {
    MethodHandle path = jnrSocketPaths.get(socketAddress.getClass());
    if (path == null) {
      return null;
    }
    try {
      return (String) path.invoke(socketAddress);
    } catch (Throwable t) {
      logger.log(FINE, "Unable to read JNR Unix domain socket path", t);
      return null;
    }
  }
}
