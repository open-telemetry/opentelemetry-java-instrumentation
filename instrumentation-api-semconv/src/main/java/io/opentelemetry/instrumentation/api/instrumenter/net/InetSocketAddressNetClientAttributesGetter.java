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
 * NetClientAttributesExtractor}. There is no meaning to implement both in the same instrumentation.
 */
public abstract class InetSocketAddressNetClientAttributesGetter<REQUEST, RESPONSE>
    implements NetClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  public abstract InetSocketAddress getPeerAddress(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  @Override
  public String sockFamily(REQUEST request, @Nullable RESPONSE response) {
    InetSocketAddress address = getPeerAddress(request, response);
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

  @Override
  @Nullable
  public final String sockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    InetSocketAddress address = getPeerAddress(request, response);
    if (address == null) {
      return null;
    }
    InetAddress remoteAddress = address.getAddress();
    if (remoteAddress != null) {
      return remoteAddress.getHostAddress();
    }
    return null;
  }

  @Override
  @Nullable
  public String sockPeerName(REQUEST request, @Nullable RESPONSE response) {
    InetSocketAddress address = getPeerAddress(request, response);
    if (address == null) {
      return null;
    }
    return address.getHostString();
  }

  @Nullable
  @Override
  public Integer sockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    InetSocketAddress address = getPeerAddress(request, response);
    if (address == null) {
      return null;
    }
    return address.getPort();
  }
}
