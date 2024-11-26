/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.common.RequestContext;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class RequestContextAccess {
  @Nullable private static final MethodHandle remoteAddress = findAccessorOrNull("remoteAddress");
  @Nullable private static final MethodHandle localAddress = findAccessorOrNull("localAddress");

  private RequestContextAccess() {}

  @Nullable
  public static InetSocketAddress remoteAddress(RequestContext requestContext) {
    return getAddress(remoteAddress, requestContext);
  }

  @Nullable
  public static InetSocketAddress localAddress(RequestContext requestContext) {
    return getAddress(localAddress, requestContext);
  }

  @Nullable
  private static InetSocketAddress getAddress(
      @Nullable MethodHandle methodHandle, RequestContext requestContext) {
    if (methodHandle != null) {
      try {
        Object address = methodHandle.invoke(requestContext);
        if (address instanceof InetSocketAddress) {
          return (InetSocketAddress) address;
        }
      } catch (Throwable throwable) {
        throw new IllegalStateException("Failed to get address", throwable);
      }
    }
    return null;
  }

  @Nullable
  private static MethodHandle findAccessorOrNull(String methodName) {
    MethodHandle methodHandle = findAccessorOrNull(methodName, SocketAddress.class);
    if (methodHandle != null) {
      return methodHandle;
    }
    // return type was changed from SocketAddress to InetSocketAddress in 1.24.0
    return findAccessorOrNull(methodName, InetSocketAddress.class);
  }

  @Nullable
  private static MethodHandle findAccessorOrNull(String methodName, Class<?> returnType) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(RequestContext.class, methodName, MethodType.methodType(returnType));
    } catch (Throwable t) {
      return null;
    }
  }
}
