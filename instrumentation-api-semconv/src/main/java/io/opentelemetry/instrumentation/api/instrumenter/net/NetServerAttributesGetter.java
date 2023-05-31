/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting server-based network attributes. It adapts an instrumentation-specific
 * request type into the 3 common attributes (transport, sockPeerPort, sockPeerAddr).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * server library/framework. It will be used by the {@link NetServerAttributesExtractor} to obtain
 * the various network attributes in a type-generic way.
 */
public interface NetServerAttributesGetter<REQUEST, RESPONSE>
    extends NetworkAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  default String getTransport(REQUEST request) {
    return null;
  }

  /**
   * Returns the application protocol used.
   *
   * <p>Examples: `amqp`, `http`, `mqtt`.
   *
   * @deprecated This method is deprecated and will be removed in the following release. Implement
   *     {@link #getNetworkProtocolName(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getProtocolName(REQUEST request) {
    return null;
  }

  /**
   * Returns the version of the application protocol used.
   *
   * <p>Examples: `3.1.1`.
   *
   * @deprecated This method is deprecated and will be removed in the following release. Implement
   *     {@link #getNetworkProtocolVersion(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getProtocolVersion(REQUEST request) {
    return null;
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getNetworkType(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getNetworkType(
        getPeerSocketAddress(request), getHostSocketAddress(request));
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getNetworkProtocolName(REQUEST request, @Nullable RESPONSE response) {
    return getProtocolName(request);
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getNetworkProtocolVersion(REQUEST request, @Nullable RESPONSE response) {
    return getProtocolVersion(request);
  }

  @Nullable
  String getHostName(REQUEST request);

  @Nullable
  Integer getHostPort(REQUEST request);

  /**
   * Returns the protocol <a
   * href="https://man7.org/linux/man-pages/man7/address_families.7.html">address family</a> which
   * is used for communication.
   *
   * <p>Examples: `inet`, `inet6`.
   *
   * <p>By default, this method attempts to retrieve the address family using one of the {@link
   * #getPeerSocketAddress(Object)} and {@link #getHostSocketAddress(Object)} methods. If neither of
   * these methods is implemented, it will simply return {@code null}. If the instrumented library
   * does not expose {@link InetSocketAddress} in its API, you might want to implement this method
   * instead of {@link #getPeerSocketAddress(Object)} and {@link #getHostSocketAddress(Object)}.
   */
  @Nullable
  default String getSockFamily(REQUEST request) {
    return InetSocketAddressUtil.getSockFamily(
        getPeerSocketAddress(request), getHostSocketAddress(request));
  }

  /**
   * Returns an {@link InetSocketAddress} object representing the peer socket address.
   *
   * <p>Implementing this method is equivalent to implementing all three of {@link
   * #getSockFamily(Object)}, {@link #getSockPeerAddr(Object)} and {@link #getSockPeerPort(Object)}.
   */
  @Nullable
  default InetSocketAddress getPeerSocketAddress(REQUEST request) {
    return null;
  }

  /**
   * Returns the remote socket peer address: IPv4 or IPv6 for internet protocols, path for local
   * communication, etc.
   *
   * <p>Examples: `127.0.0.1`, `/tmp/mysql.sock`.
   *
   * <p>By default, this method attempts to retrieve the peer address using the {@link
   * #getPeerSocketAddress(Object)} method. If this method is not implemented, it will simply return
   * {@code null}. If the instrumented library does not expose {@link InetSocketAddress} in its API,
   * you might want to implement this method instead of {@link #getPeerSocketAddress(Object)}.
   */
  @Nullable
  default String getSockPeerAddr(REQUEST request) {
    return InetSocketAddressUtil.getHostAddress(getPeerSocketAddress(request));
  }

  /**
   * Returns the remote socket peer port.
   *
   * <p>Examples: `16456`.
   *
   * <p>By default, this method attempts to retrieve the peer port using the {@link
   * #getPeerSocketAddress(Object)} method. If this method is not implemented, it will simply return
   * {@code null}. If the instrumented library does not expose {@link InetSocketAddress} in its API,
   * you might want to implement this method instead of {@link #getPeerSocketAddress(Object)}.
   */
  @Nullable
  default Integer getSockPeerPort(REQUEST request) {
    return InetSocketAddressUtil.getPort(getPeerSocketAddress(request));
  }

  /**
   * Returns an {@link InetSocketAddress} object representing the host socket address.
   *
   * <p>Implementing this method is equivalent to implementing all three of {@link
   * #getSockFamily(Object)}, {@link #getSockHostAddr(Object)} and {@link #getSockHostPort(Object)}.
   */
  @Nullable
  default InetSocketAddress getHostSocketAddress(REQUEST request) {
    return null;
  }

  /**
   * Returns the local socket address. Useful in case of a multi-IP host.
   *
   * <p>Examples: `192.168.0.1`.
   *
   * <p>By default, this method attempts to retrieve the host address using the {@link
   * #getHostSocketAddress(Object)} method. If this method is not implemented, it will simply return
   * {@code null}. If the instrumented library does not expose {@link InetSocketAddress} in its API,
   * you might want to implement this method instead of {@link #getHostSocketAddress(Object)}.
   */
  @Nullable
  default String getSockHostAddr(REQUEST request) {
    return InetSocketAddressUtil.getHostAddress(getHostSocketAddress(request));
  }

  /**
   * Returns the local socket port number.
   *
   * <p>Examples: `35555`.
   *
   * <p>By default, this method attempts to retrieve the host port using the {@link
   * #getHostSocketAddress(Object)} method. If this method is not implemented, it will simply return
   * {@code null}. If the instrumented library does not expose {@link InetSocketAddress} in its API,
   * you might want to implement this method instead of {@link #getHostSocketAddress(Object)}.
   */
  @Nullable
  default Integer getSockHostPort(REQUEST request) {
    return InetSocketAddressUtil.getPort(getHostSocketAddress(request));
  }
}
