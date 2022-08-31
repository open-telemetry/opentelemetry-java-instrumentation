/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a> from a {@link InetSocketAddress}. Most network libraries will provide access to a
 * {@link InetSocketAddress} so this is a convenient alternative to {@link
 * NetServerAttributesExtractor}. There is no meaning to implement both in the same instrumentation.
 */
public abstract class InetSocketAddressNetServerAttributesGetter<REQUEST>
    implements NetServerAttributesGetter<REQUEST> {

  @Nullable
  public abstract InetSocketAddress getPeerAddress(REQUEST request);

  // optional
  @Nullable
  public abstract InetSocketAddress getHostAddress(REQUEST request);

  @Nullable
  @Override
  public String sockFamily(REQUEST request) {
    InetSocketAddress address = getPeerAddress(request);
    if (address == null) {
      address = getHostAddress(request);
    }
    if (address == null) {
      return null;
    }
    InetAddress inetAddress = address.getAddress();
    if (inetAddress instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

  @Override
  @Nullable
  public final String sockPeerAddr(REQUEST request) {
    return getAddress(getPeerAddress(request));
  }

  @Override
  @Nullable
  public final Integer sockPeerPort(REQUEST request) {
    return getPort(getPeerAddress(request));
  }

  @Nullable
  @Override
  public String sockHostAddr(REQUEST request) {
    return getAddress(getHostAddress(request));
  }

  @Nullable
  @Override
  public Integer sockHostPort(REQUEST request) {
    return getPort(getHostAddress(request));
  }

  @Nullable
  private static String getAddress(InetSocketAddress address) {
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress != null) {
      return remoteAddress.getHostAddress();
    }
    return null;
  }

  @Nullable
  private static Integer getPort(InetSocketAddress address) {
    if (address == null) {
      return null;
    }
    return address.getPort();
  }
}
