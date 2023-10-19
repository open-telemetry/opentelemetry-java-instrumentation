/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InetSocketAddressUtil;
import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting server-based network attributes. It adapts an instrumentation-specific
 * request type into the 3 common attributes (transport, sockPeerPort, sockPeerAddr).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * server library/framework. It will be used by the {@link NetServerAttributesExtractor} to obtain
 * the various network attributes in a type-generic way.
 *
 * @deprecated Make sure that your instrumentation implements the getters from the {@code
 *     ...network} package instead. This class will be removed in the 2.0 release.
 */
@Deprecated
public interface NetServerAttributesGetter<REQUEST, RESPONSE>
    extends NetworkAttributesGetter<REQUEST, RESPONSE>,
        ServerAttributesGetter<REQUEST, RESPONSE>,
        ClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  default String getTransport(REQUEST request) {
    return null;
  }

  /**
   * Returns the protocol <a
   * href="https://man7.org/linux/man-pages/man7/address_families.7.html">address family</a> which
   * is used for communication.
   *
   * <p>Examples: `inet`, `inet6`.
   *
   * <p>By default, this method attempts to retrieve the address family using one of the {@link
   * #getClientInetSocketAddress(Object, Object)} and {@link #getServerInetSocketAddress(Object,
   * Object)} methods. If neither of these methods is implemented, it will simply return {@code
   * null}. If the instrumented library does not expose {@link InetSocketAddress} in its API, you
   * might want to implement this method instead of {@link #getClientInetSocketAddress(Object,
   * Object)} and {@link #getServerInetSocketAddress(Object, Object)}.
   */
  @Nullable
  default String getSockFamily(REQUEST request) {
    return InetSocketAddressUtil.getSockFamily(
        getClientInetSocketAddress(request, null), getServerInetSocketAddress(request, null));
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getNetworkType(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getNetworkType(
        getClientInetSocketAddress(request, response),
        getServerInetSocketAddress(request, response));
  }

  @Nullable
  @Override
  default InetSocketAddress getServerInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return getNetworkLocalInetSocketAddress(request, response);
  }

  @Nullable
  @Override
  default String getServerSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkLocalAddress(request, response);
  }

  @Nullable
  @Override
  default Integer getServerSocketPort(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkLocalPort(request, response);
  }

  @Nullable
  @Override
  default InetSocketAddress getClientInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerInetSocketAddress(request, response);
  }

  @Nullable
  @Override
  default String getClientSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerAddress(request, response);
  }

  @Nullable
  @Override
  default Integer getClientSocketPort(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerPort(request, response);
  }
}
