/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.semconv.network.UnixSocketAddress;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcSocketAddressUtil {

  @Nullable
  public static SocketAddress normalize(@Nullable SocketAddress address) {
    if (address == null || address instanceof InetSocketAddress) {
      return address;
    }
    String name = address.getClass().getName();
    if ((!name.equals("io.netty.channel.unix.DomainSocketAddress")
            && !name.equals("io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress"))
        || !RuntimeSupport.available) {
      return address;
    }
    return NettySocketSupport.normalize(address);
  }

  private GrpcSocketAddressUtil() {}

  private static final class RuntimeSupport {
    private static final boolean available = isAvailable();

    private static boolean isAvailable() {
      ClassLoader loader = GrpcSocketAddressUtil.class.getClassLoader();
      try {
        Class.forName("java.lang.ClassValue", false, loader);
        Class.forName("java.lang.invoke.MethodHandle", false, loader);
        return true;
      } catch (ClassNotFoundException ignored) {
        return false;
      }
    }
  }

  @IgnoreJRERequirement
  private static final class NettySocketSupport {
    private static final Logger logger = Logger.getLogger(NettySocketSupport.class.getName());

    // The same Netty class name can be loaded by multiple application class loaders.
    private static final ClassValue<MethodHandle> pathAccessors =
        new ClassValue<MethodHandle>() {
          @Override
          @Nullable
          protected MethodHandle computeValue(Class<?> type) {
            try {
              return MethodHandles.publicLookup()
                  .findVirtual(type, "path", MethodType.methodType(String.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
              logger.log(FINE, "Unable to find Netty Unix domain socket path accessor", e);
              return null;
            }
          }
        };

    private static SocketAddress normalize(SocketAddress address) {
      @Nullable MethodHandle accessor = pathAccessors.get(address.getClass());
      if (accessor != null) {
        try {
          // Unlike invokeExact, this ordinary call can be dexed for older Android versions.
          return new UnixSocketAddress((String) accessor.invokeWithArguments(address));
        } catch (Throwable t) {
          logger.log(FINE, "Unable to read Netty Unix domain socket path", t);
        }
      }
      return address;
    }
  }
}
