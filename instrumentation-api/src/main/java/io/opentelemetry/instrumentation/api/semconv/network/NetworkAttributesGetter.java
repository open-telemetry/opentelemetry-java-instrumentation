/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import io.opentelemetry.instrumentation.api.semconv.network.internal.SocketAddressUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
    return SocketAddressUtil.getNetworkType(
        getNetworkLocalSocketAddress(request, response),
        getNetworkPeerSocketAddress(request, response));
  }

  /**
   * Returns the <a href="https://osi-model.com/transport-layer/">OSI transport layer</a> or <a
   * href="https://en.wikipedia.org/wiki/Inter-process_communication">inter-process communication
   * method</a>.
   *
   * <p>Examples: {@code tcp}, {@code udp}, {@code unix}
   *
   * <p>By default, returns {@code unix} when either socket address is a supported Unix domain
   * socket address, and {@code null} otherwise. Internet socket addresses do not imply a transport.
   */
  @Nullable
  default String getNetworkTransport(REQUEST request, @Nullable RESPONSE response) {
    return SocketAddressUtil.getNetworkTransport(
        getNetworkLocalSocketAddress(request, response),
        getNetworkPeerSocketAddress(request, response));
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
   *
   * @deprecated Use {@link #getNetworkLocalSocketAddress(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default InetSocketAddress getNetworkLocalInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the local socket address.
   *
   * <p>The default delegates to {@link #getNetworkLocalInetSocketAddress(Object, Object)} to
   * preserve existing implementations. Override this method for all socket address types. Defaults
   * for address, port, network type, and transport use this method; explicitly overridden attribute
   * getters take precedence.
   *
   * <p>Supported types are {@link InetSocketAddress} and JDK 16+ {@code UnixDomainSocketAddress}.
   * Unix socket addresses supply a path and {@code unix} transport, but no port or network type.
   * Empty Unix paths are omitted.
   */
  @Nullable
  default SocketAddress getNetworkLocalSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkLocalInetSocketAddress(request, response);
  }

  /**
   * Returns the local address of the network connection - IP address or Unix domain socket name.
   *
   * <p>Examples: {@code 10.1.2.80}, {@code /tmp/my.sock}
   *
   * <p>By default, reads {@link #getNetworkLocalSocketAddress(Object, Object)}. Returns {@code
   * null} for absent, unsupported, or unresolved addresses and empty Unix paths. Override this
   * method for custom address representations.
   */
  @Nullable
  default String getNetworkLocalAddress(REQUEST request, @Nullable RESPONSE response) {
    return SocketAddressUtil.getAddress(getNetworkLocalSocketAddress(request, response));
  }

  /**
   * Returns the local port number of the network connection.
   *
   * <p>Examples: {@code 65123}
   *
   * <p>By default, reads {@link #getNetworkLocalSocketAddress(Object, Object)}. Returns a port only
   * for {@link InetSocketAddress}, including unresolved addresses, and {@code null} otherwise.
   * Override this method for custom port representations.
   */
  @Nullable
  default Integer getNetworkLocalPort(REQUEST request, @Nullable RESPONSE response) {
    return SocketAddressUtil.getPort(getNetworkLocalSocketAddress(request, response));
  }

  /**
   * Returns an {@link InetSocketAddress} object representing the peer socket address.
   *
   * <p>Implementing this method is equivalent to implementing both {@link
   * #getNetworkPeerAddress(Object, Object)} and {@link #getNetworkPeerPort(Object, Object)}.
   *
   * @deprecated Use {@link #getNetworkPeerSocketAddress(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default InetSocketAddress getNetworkPeerInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the peer socket address.
   *
   * <p>The default delegates to {@link #getNetworkPeerInetSocketAddress(Object, Object)} to
   * preserve existing implementations. Override this method for all socket address types. Defaults
   * for address, port, network type, and transport use this method; explicitly overridden attribute
   * getters take precedence.
   *
   * <p>See {@link #getNetworkLocalSocketAddress(Object, Object)} for supported address types.
   */
  @Nullable
  default SocketAddress getNetworkPeerSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerInetSocketAddress(request, response);
  }

  /**
   * Returns the peer address of the network connection - IP address or Unix domain socket name.
   *
   * <p>Examples: {@code 10.1.2.80}, {@code /tmp/my.sock}
   *
   * <p>By default, reads {@link #getNetworkPeerSocketAddress(Object, Object)}. Returns {@code null}
   * for absent, unsupported, or unresolved addresses and empty Unix paths. Override this method for
   * custom address representations.
   */
  @Nullable
  default String getNetworkPeerAddress(REQUEST request, @Nullable RESPONSE response) {
    return SocketAddressUtil.getAddress(getNetworkPeerSocketAddress(request, response));
  }

  /**
   * Returns the peer port number of the network connection.
   *
   * <p>Examples: {@code 65123}
   *
   * <p>By default, reads {@link #getNetworkPeerSocketAddress(Object, Object)}. Returns a port only
   * for {@link InetSocketAddress}, including unresolved addresses, and {@code null} otherwise.
   * Override this method for custom port representations.
   */
  @Nullable
  default Integer getNetworkPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return SocketAddressUtil.getPort(getNetworkPeerSocketAddress(request, response));
  }
}
