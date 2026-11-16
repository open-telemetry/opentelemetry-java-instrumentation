/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static java.util.logging.Level.FINE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SocketAddressUtil {

  private static final boolean unixSocketSupported =
      isPresent("java.lang.ClassValue")
          && isPresent("java.lang.invoke.MethodHandle")
          && isPresent("java.nio.file.Path");

  private static boolean isPresent(String name) {
    try {
      Class.forName(name, false, SocketAddressUtil.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  @Nullable
  public static String getNetworkType(
      @Nullable SocketAddress address, @Nullable SocketAddress otherAddress) {
    if (address == null) {
      address = otherAddress;
    }
    return address instanceof InetSocketAddress
        ? InetSocketAddressUtil.getNetworkType((InetSocketAddress) address, null)
        : null;
  }

  @Nullable
  public static String getNetworkTransport(
      @Nullable SocketAddress address, @Nullable SocketAddress otherAddress) {
    return isUnixSocket(address) || isUnixSocket(otherAddress) ? "unix" : null;
  }

  private static boolean isUnixSocket(@Nullable SocketAddress address) {
    return address != null && unixSocketSupported && UnixSocketSupport.isUnixSocket(address);
  }

  @Nullable
  public static String getAddress(@Nullable SocketAddress address) {
    if (address instanceof InetSocketAddress) {
      return InetSocketAddressUtil.getIpAddress((InetSocketAddress) address);
    }
    if (address == null || !unixSocketSupported) {
      return null;
    }
    return UnixSocketSupport.getAddress(address);
  }

  @Nullable
  public static Integer getPort(@Nullable SocketAddress address) {
    return address instanceof InetSocketAddress
        ? InetSocketAddressUtil.getPort((InetSocketAddress) address)
        : null;
  }

  private SocketAddressUtil() {}

  // Keep APIs unavailable on older Android runtimes behind the capability-gated lazy holder.
  @IgnoreJRERequirement
  private static final class UnixSocketSupport {
    private static final Logger logger = Logger.getLogger(UnixSocketSupport.class.getName());

    // This helper is shared across application loaders, so optional types must be resolved and
    // cached by their runtime class rather than by the helper's defining loader.
    private static final ClassValue<MethodHandle> unixSocketAccessors =
        new ClassValue<MethodHandle>() {
          @Override
          @Nullable
          protected MethodHandle computeValue(Class<?> type) {
            return createAccessor(type);
          }
        };

    @Nullable
    private static MethodHandle createAccessor(Class<?> type) {
      String name = type.getName();
      String method;
      Class<?> returnType;
      if (name.equals("java.net.UnixDomainSocketAddress")) {
        method = "getPath";
        returnType = Path.class;
      } else if (name.equals("io.netty.channel.unix.DomainSocketAddress")
          || name.equals("io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress")) {
        method = "path";
        returnType = String.class;
      } else {
        return null;
      }
      try {
        return MethodHandles.publicLookup()
            .findVirtual(type, method, MethodType.methodType(returnType));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        return null;
      }
    }

    private static boolean isUnixSocket(SocketAddress address) {
      @Nullable MethodHandle path = unixSocketAccessors.get(address.getClass());
      return path != null;
    }

    @Nullable
    private static String getAddress(SocketAddress address) {
      @Nullable MethodHandle path = unixSocketAccessors.get(address.getClass());
      if (path == null) {
        return null;
      }
      try {
        // Unlike invokeExact, this ordinary call can be dexed for older Android versions.
        Object value = path.invokeWithArguments(address);
        String name = value instanceof Path ? value.toString() : (String) value;
        return name == null || name.isEmpty() ? null : name;
      } catch (Throwable t) {
        logger.log(FINE, "Unable to read Unix domain socket path", t);
        return null;
      }
    }
  }
}
