/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InetSocketAddressUtil;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An interface for getting client-based network attributes. It adapts from a type-specific request
 * and response into the 4 common network attribute values.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the NetClientAttributesExtractor to obtain the various
 * network attributes in a type-generic way.
 *
 * @deprecated Make sure that your instrumentation implements the getters from the {@code
 *     ...network} package instead. This class will be removed in the 2.0 release.
 */
@Deprecated
public interface NetClientAttributesGetter<REQUEST, RESPONSE>
    extends NetworkAttributesGetter<REQUEST, RESPONSE>, ServerAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  default String getTransport(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the protocol <a
   * href="https://man7.org/linux/man-pages/man7/address_families.7.html">address family</a> which
   * is used for communication.
   *
   * <p>Examples: {@code inet}, {@code inet6}
   *
   * <p>By default, this method attempts to retrieve the address family using the {@link
   * #getServerInetSocketAddress(Object, Object)} method. If it is not implemented, it will simply
   * return {@code null}. If the instrumented library does not expose {@link InetSocketAddress} in
   * its API, you might want to implement this method instead of {@link
   * #getServerSocketAddress(Object, Object)}.
   */
  @Nullable
  default String getSockFamily(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getSockFamily(getServerInetSocketAddress(request, response), null);
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getNetworkType(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getNetworkType(
        getServerInetSocketAddress(request, response), null);
  }

  @Nullable
  @Override
  default InetSocketAddress getServerInetSocketAddress(
      REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerInetSocketAddress(request, response);
  }

  @Nullable
  @Override
  default String getServerSocketAddress(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerAddress(request, response);
  }

  @Nullable
  @Override
  default Integer getServerSocketPort(REQUEST request, @Nullable RESPONSE response) {
    return getNetworkPeerPort(request, response);
  }
}
