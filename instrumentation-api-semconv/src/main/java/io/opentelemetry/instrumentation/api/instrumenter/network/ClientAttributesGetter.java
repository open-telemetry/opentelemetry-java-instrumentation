/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InetSocketAddressUtil;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting attributes describing a network client.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link ClientAttributesExtractor} (or other convention
 * specific extractors) to obtain the various server attributes in a type-generic way.
 */
public interface ClientAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the client address - unix domain socket name, IPv4 or IPv6 address.
   *
   * <p>Examples: {@code /tmp/my.sock}, {@code 10.1.2.80}
   */
  @Nullable
  default String getClientAddress(REQUEST request) {
    return null;
  }

  /**
   * Returns the client port number.
   *
   * <p>Examples: {@code 65123}
   */
  @Nullable
  default Integer getClientPort(REQUEST request) {
    return null;
  }

  /**
   * Returns an {@link InetSocketAddress} object representing the immediate client socket address.
   *
   * <p>Implementing this method is equivalent to implementing all of {@link
   * #getClientSocketAddress(Object, Object)} and {@link #getClientSocketPort(Object, Object)}.
   *
   * @deprecated Implement {@link NetworkAttributesGetter#getNetworkPeerInetSocketAddress(Object,
   *     Object)} instead.
   */
  @Deprecated
  @Nullable
  default InetSocketAddress getClientInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the immediate client peer address - unix domain socket name, IPv4 or IPv6 address.
   *
   * <p>Examples: {@code /tmp/my.sock}, {@code 127.0.0.1}
   *
   * <p>By default, this method attempts to retrieve the immediate client address using the {@link
   * #getClientInetSocketAddress(Object, Object)} method. If this method is not implemented, it will
   * simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getClientInetSocketAddress(Object, Object)}.
   *
   * @deprecated Implement {@link NetworkAttributesGetter#getNetworkPeerAddress(Object, Object)}
   *     instead.
   */
  @Deprecated
  @Nullable
  default String getClientSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getIpAddress(getClientInetSocketAddress(request, response));
  }

  /**
   * Returns the immediate client peer port number.
   *
   * <p>Examples: {@code 35555}
   *
   * <p>By default, this method attempts to retrieve the immediate client port using the {@link
   * #getClientInetSocketAddress(Object, Object)} method. If this method is not implemented, it will
   * simply return {@code null}. If the instrumented library does not expose {@link
   * InetSocketAddress} in its API, you might want to implement this method instead of {@link
   * #getClientInetSocketAddress(Object, Object)}.
   *
   * @deprecated Implement {@link NetworkAttributesGetter#getNetworkPeerPort(Object, Object)}
   *     instead.
   */
  @Deprecated
  @Nullable
  default Integer getClientSocketPort(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getPort(getClientInetSocketAddress(request, response));
  }
}
