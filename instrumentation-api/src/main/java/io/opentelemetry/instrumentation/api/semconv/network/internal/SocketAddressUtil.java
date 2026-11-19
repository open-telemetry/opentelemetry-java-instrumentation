/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.semconv.network.UnixSocketAddress;
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

  // ClassValue, MethodHandle, and Path are unavailable on older Android runtimes.
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
    return address instanceof UnixSocketAddress
        || (address != null && unixSocketSupported && UnixSocketSupport.isUnixSocket(address));
  }

  @Nullable
  public static String getAddress(@Nullable SocketAddress address) {
    if (address instanceof InetSocketAddress) {
      return InetSocketAddressUtil.getIpAddress((InetSocketAddress) address);
    }
    if (address instanceof UnixSocketAddress) {
      String path = ((UnixSocketAddress) address).getPath();
      return path.isEmpty() ? null : path;
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

    private static final ClassValue<MethodHandle> unixSocketAccessors =
        new ClassValue<MethodHandle>() {
          @Override
          @Nullable
          protected MethodHandle computeValue(Class<?> type) {
            if (!type.getName().equals("java.net.UnixDomainSocketAddress")) {
              return null;
            }
            try {
              return MethodHandles.publicLookup()
                  .findVirtual(type, "getPath", MethodType.methodType(Path.class));
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
              return null;
            }
          }
        };

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
        Path value = (Path) path.invokeWithArguments(address);
        String name = value.toString();
        return name.isEmpty() ? null : name;
      } catch (Throwable t) {
        logger.log(FINE, "Unable to read Unix domain socket path", t);
        return null;
      }
    }
  }
}
