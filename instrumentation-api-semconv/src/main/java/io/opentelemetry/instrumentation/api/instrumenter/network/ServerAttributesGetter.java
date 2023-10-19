/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InetSocketAddressUtil;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting attributes describing a network server.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link ServerAttributesExtractor} (or other convention
 * specific extractors) to obtain the various server attributes in a type-generic way.
 */
public interface ServerAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Return the logical server hostname that matches server FQDN if available, and IP or socket
   * address if FQDN is not known.
   *
   * <p>Examples: {@code example.com}
   */
  @Nullable
  default String getServerAddress(REQUEST request) {
    return null;
  }

  /**
   * Return the logical server port number.
   *
   * <p>Examples: {@code 80}, {@code 8080}, {@code 443}
   */
  @Nullable
  default Integer getServerPort(REQUEST request) {
    return null;
  }

  // TODO deprecate

  /**
   * Returns an {@link InetSocketAddress} object representing the server socket address.
   *
   * <p>Implementing this method is equivalent to implementing all of {@link
   * #getServerSocketDomain(Object, Object)}, {@link #getServerSocketAddress(Object, Object)} and
   * {@link #getServerSocketPort(Object, Object)}.
   *
   * @deprecated Implement {@link NetworkAttributesGetter#getNetworkPeerInetSocketAddress(Object,
   *     Object)} or {@link NetworkAttributesGetter#getNetworkLocalInetSocketAddress(Object,
   *     Object)} instead.
   */
  @Deprecated
  @Nullable
  default InetSocketAddress getServerInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Return the domain name of an immediate peer.
   *
   * <p>Examples: {@code proxy.example.com}
   *
   * <p>By default, this method attempts to retrieve the server domain name using the {@link
   * #getServerInetSocketAddress(Object, Object)} method. If this method is not implemented, it will
   * simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getServerInetSocketAddress(Object, Object)}.
   *
   * @deprecated This method is deprecated and will be removed without replacement.
   */
  @Deprecated
  @Nullable
  default String getServerSocketDomain(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getDomainName(getServerInetSocketAddress(request, response));
  }

  /**
   * Return the physical server IP address or Unix socket address.
   *
   * <p>Examples: {@code 10.5.3.2}
   *
   * <p>By default, this method attempts to retrieve the server address using the {@link
   * #getServerInetSocketAddress(Object, Object)} method. If this method is not implemented, it will
   * simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getServerInetSocketAddress(Object, Object)}.
   *
   * @deprecated Implement {@link NetworkAttributesGetter#getNetworkPeerAddress(Object, Object)} or
   *     {@link NetworkAttributesGetter#getNetworkLocalAddress(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getServerSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getIpAddress(getServerInetSocketAddress(request, response));
  }

  /**
   * Return the physical server port.
   *
   * <p>Examples: {@code 16456}
   *
   * <p>By default, this method attempts to retrieve the server port using the {@link
   * #getServerInetSocketAddress(Object, Object)} method. If this method is not implemented, it will
   * simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getServerInetSocketAddress(Object, Object)}.
   *
   * @deprecated Implement {@link NetworkAttributesGetter#getNetworkPeerPort(Object, Object)} or
   *     {@link NetworkAttributesGetter#getNetworkLocalPort(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default Integer getServerSocketPort(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getPort(getServerInetSocketAddress(request, response));
  }
}
