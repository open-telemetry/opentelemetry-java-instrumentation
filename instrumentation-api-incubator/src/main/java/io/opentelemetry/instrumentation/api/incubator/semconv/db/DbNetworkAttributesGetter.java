/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import io.opentelemetry.instrumentation.api.semconv.network.internal.InetSocketAddressUtil;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting database network attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link DbNetworkAttributesExtractor} (or other convention
 * specific extractors) to obtain the various database network attributes in a type-generic way.
 */
public interface DbNetworkAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the <a href="https://osi-model.com/network-layer/">OSI network layer</a> or non-OSI
   * equivalent.
   *
   * <p>Examples: {@code ipv4}, {@code ipv6}
   */
  @Deprecated
  @Nullable
  default String getNetworkType(REQUEST request, @Nullable RESPONSE response) {
    InetSocketAddress address = getNetworkPeerInetSocketAddress(request, response);
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

  /**
   * Returns an {@link InetSocketAddress} object representing the peer socket address.
   *
   * <p>Implementing this method is equivalent to implementing both {@link
   * #getNetworkPeerAddress(Object, Object)} and {@link #getNetworkPeerPort(Object, Object)}.
   */
  @Nullable
  default InetSocketAddress getNetworkPeerInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the peer address of the network connection - IP address or Unix domain socket name.
   *
   * <p>Examples: {@code 10.1.2.80}, {@code /tmp/my.sock}
   *
   * <p>By default, this method attempts to retrieve the peer address using the {@link
   * #getNetworkPeerInetSocketAddress(Object, Object)} method. If that method is not implemented, it
   * will simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getNetworkPeerInetSocketAddress(Object, Object)}.
   */
  @Nullable
  default String getNetworkPeerAddress(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getIpAddress(getNetworkPeerInetSocketAddress(request, response));
  }

  /**
   * Returns the peer port number of the network connection.
   *
   * <p>Examples: {@code 65123}
   *
   * <p>By default, this method attempts to retrieve the peer port using the {@link
   * #getNetworkPeerInetSocketAddress(Object, Object)} method. If that method is not implemented, it
   * will simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getNetworkPeerInetSocketAddress(Object, Object)}.
   */
  @Nullable
  default Integer getNetworkPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getPort(getNetworkPeerInetSocketAddress(request, response));
  }
}
