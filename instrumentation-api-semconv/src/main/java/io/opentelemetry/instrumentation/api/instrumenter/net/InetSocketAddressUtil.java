/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class InetSocketAddressUtil {

  @Nullable
  static String getSockFamily(
      @Nullable InetSocketAddress address, @Nullable InetSocketAddress otherAddress) {
    if (address == null) {
      address = otherAddress;
    }
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

  @Nullable
  static String getHostName(@Nullable InetSocketAddress address) {
    if (address == null) {
      return null;
    }
    return address.getHostString();
  }

  @Nullable
  static String getHostAddress(@Nullable InetSocketAddress address) {
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
  static Integer getPort(@Nullable InetSocketAddress address) {
    if (address == null) {
      return null;
    }
    return address.getPort();
  }

  private InetSocketAddressUtil() {}
}
