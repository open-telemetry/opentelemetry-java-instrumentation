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
  default String getTransport(REQUEST request, @Nullable RESPONSE response) {
    return transport(request, response);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getTransport(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String transport(REQUEST request, @Nullable RESPONSE response) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getPeerName(REQUEST request) {
    return peerName(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getPeerName(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String peerName(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default Integer getPeerPort(REQUEST request) {
    return peerPort(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getPeerPort(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Integer peerPort(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  // TODO: when removing sockFamily(), make sure this method returns null by default
  @Nullable
  default String getSockFamily(REQUEST request, @Nullable RESPONSE response) {
    return sockFamily(request, response);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockFamily(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String sockFamily(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  // TODO: when removing sockPeerAddr(), make sure this method returns null by default
  @Nullable
  default String getSockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return sockPeerAddr(request, response);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockPeerAddr(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String sockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  // TODO: when removing sockPeerName(), make sure this method returns null by default
  @Nullable
  default String getSockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return sockPeerName(request, response);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockPeerName(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String sockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  // TODO: when removing sockPeerPort(), make sure this method returns null by default
  @Nullable
  default Integer getSockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return sockPeerPort(request, response);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockPeerName(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default Integer sockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
