/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class InetSocketAddressNetAttributesExtractor<REQUEST, RESPONSE>
    extends NetAttributesExtractor<REQUEST, RESPONSE> {

  @Nullable
  protected abstract InetSocketAddress getAddress(REQUEST request, RESPONSE response);

  @Override
  @Nullable
  protected final String peerName(REQUEST request, RESPONSE response) {
    InetSocketAddress address = getAddress(request, response);
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress != null) {
      return remoteAddress.getHostName();
    }
    // Failed DNS lookup, the host string is the name.
    return address.getHostString();
  }

  @Override
  @Nullable
  protected final Long peerPort(REQUEST request, RESPONSE response) {
    InetSocketAddress address = getAddress(request, response);
    if (address == null) {
      return null;
    }
    return (long) address.getPort();
  }

  @Override
  @Nullable
  protected final String peerIp(REQUEST request, RESPONSE response) {
    InetSocketAddress address = getAddress(request, response);
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress != null) {
      return remoteAddress.getHostAddress();
    }
    return null;
  }
}
