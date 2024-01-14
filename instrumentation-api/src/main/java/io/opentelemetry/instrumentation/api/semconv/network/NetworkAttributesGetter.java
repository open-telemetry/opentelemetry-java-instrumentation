/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import io.opentelemetry.instrumentation.api.semconv.network.internal.InetSocketAddressUtil;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting network attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link NetworkAttributesExtractor} (or other convention
 * specific extractors) to obtain the various network attributes in a type-generic way.
 *
 * @since 2.0.0
 */
public interface NetworkAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the <a href="https://osi-model.com/network-layer/">OSI network layer</a> or non-OSI
   * equivalent.
   *
   * <p>Examples: {@code ipv4}, {@code ipv6}
   */
  @Nullable
  default String getNetworkType(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getNetworkType(
        getNetworkLocalInetSocketAddress(request, response),
        getNetworkPeerInetSocketAddress(request, response));
  }

  /**
   * Returns the <a href="https://osi-model.com/transport-layer/">OSI transport layer</a> or <a
   * href="https://en.wikipedia.org/wiki/Inter-process_communication">inter-process communication
   * method</a>.
   *
   * <p>Examples: {@code tcp}, {@code udp}
   */
  @Nullable
  default String getNetworkTransport(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the <a href="https://osi-model.com/application-layer/">OSI application layer</a> or
   * non-OSI equivalent.
   *
   * <p>Examples: {@code ampq}, {@code http}, {@code mqtt}
   */
  @Nullable
  default String getNetworkProtocolName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the version of the protocol returned by {@link #getNetworkProtocolName(Object,
   * Object)}.
   *
   * <p>Examples: {@code 3.1.1}
   */
  @Nullable
  default String getNetworkProtocolVersion(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns an {@link InetSocketAddress} object representing the local socket address.
   *
   * <p>Implementing this method is equivalent to implementing both {@link
   * #getNetworkLocalAddress(Object, Object)} and {@link #getNetworkLocalPort(Object, Object)}.
   */
  @Nullable
  default InetSocketAddress getNetworkLocalInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the local address of the network connection - IP address or Unix domain socket name.
   *
   * <p>Examples: {@code 10.1.2.80}, {@code /tmp/my.sock}
   *
   * <p>By default, this method attempts to retrieve the local address using the {@link
   * #getNetworkLocalInetSocketAddress(Object, Object)} method. If that method is not implemented,
   * it will simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getNetworkLocalInetSocketAddress(Object, Object)}.
   */
  @Nullable
  default String getNetworkLocalAddress(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getIpAddress(getNetworkLocalInetSocketAddress(request, response));
  }

  /**
   * Returns the local port number of the network connection.
   *
   * <p>Examples: {@code 65123}
   *
   * <p>By default, this method attempts to retrieve the local port using the {@link
   * #getNetworkLocalInetSocketAddress(Object, Object)} method. If that method is not implemented,
   * it will simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getNetworkLocalInetSocketAddress(Object, Object)}.
   */
  @Nullable
  default Integer getNetworkLocalPort(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getPort(getNetworkLocalInetSocketAddress(request, response));
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
