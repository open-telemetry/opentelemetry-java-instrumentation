/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InetSocketAddressUtil {

  @Nullable
  public static String getNetworkType(
      @Nullable InetSocketAddress address, @Nullable InetSocketAddress otherAddress) {
    if (address == null) {
      address = otherAddress;
    }
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress instanceof Inet4Address) {
      return "ipv4";
    } else if (remoteAddress instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Nullable
  public static String getIpAddress(@Nullable InetSocketAddress address) {
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress == null) {
      return null;
    }
    return remoteAddress.getHostAddress();
  }

  @Nullable
  public static Integer getPort(@Nullable InetSocketAddress address) {
    if (address == null) {
      return null;
    }
    return address.getPort();
  }

  private InetSocketAddressUtil() {}
}
