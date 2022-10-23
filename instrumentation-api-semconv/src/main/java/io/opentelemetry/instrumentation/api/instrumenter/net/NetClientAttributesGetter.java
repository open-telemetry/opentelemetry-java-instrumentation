/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import javax.annotation.Nullable;

/**
 * An interface for getting client-based network attributes. It adapts from a type-specific request
 * and response into the 4 common network attribute values.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the NetClientAttributesExtractor to obtain the various
 * network attributes in a type-generic way.
 */
public interface NetClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  String transport(REQUEST request, @Nullable RESPONSE response);

  /**
   * Logical remote hostname.
   *
   * @deprecated This method is deprecated and will be removed in the next release.
   */
  @Deprecated
  @Nullable
  default String peerName(REQUEST request, @Nullable RESPONSE response) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the next release");
  }

  @Nullable
  default String peerName(REQUEST request) {
    return peerName(request, null);
  }

  /**
   * Logical remote port number.
   *
   * @deprecated This method is deprecated and will be removed in the next release.
   */
  @Deprecated
  @Nullable
  default Integer peerPort(REQUEST request, @Nullable RESPONSE response) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the next release");
  }

  @Nullable
  default Integer peerPort(REQUEST request) {
    return peerPort(request, null);
  }

  @Nullable
  default String sockFamily(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String sockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String sockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default Integer sockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
