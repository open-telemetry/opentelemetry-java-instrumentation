/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import javax.annotation.Nullable;

/**
 * An interface for getting server-based network attributes. It adapts an instrumentation-specific
 * request type into the 3 common attributes (transport, sockPeerPort, sockPeerAddr).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * server library/framework. It will be used by the {@link NetServerAttributesExtractor} to obtain
 * the various network attributes in a type-generic way.
 */
public interface NetServerAttributesGetter<REQUEST> {

  @Nullable
  default String getTransport(REQUEST request) {
    return transport(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getTransport(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String transport(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getHostName(REQUEST request) {
    return hostName(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getHostName(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String hostName(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default Integer getHostPort(REQUEST request) {
    return hostPort(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getHostPort(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Integer hostPort(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  // TODO: when removing sockFamily(), make sure this method returns null by default
  @Nullable
  default String getSockFamily(REQUEST request) {
    return sockFamily(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockFamily(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String sockFamily(REQUEST request) {
    return null;
  }

  // TODO: when removing sockPeerAddr(), make sure this method returns null by default
  @Nullable
  default String getSockPeerAddr(REQUEST request) {
    return sockPeerAddr(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockPeerAddr(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String sockPeerAddr(REQUEST request) {
    return null;
  }

  // TODO: when removing sockPeerPort(), make sure this method returns null by default
  @Nullable
  default Integer getSockPeerPort(REQUEST request) {
    return sockPeerPort(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockPeerPort(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Integer sockPeerPort(REQUEST request) {
    return null;
  }

  // TODO: when removing sockHostAddr(), make sure this method returns null by default
  @Nullable
  default String getSockHostAddr(REQUEST request) {
    return sockHostAddr(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockHostAddr(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String sockHostAddr(REQUEST request) {
    return null;
  }

  // TODO: when removing sockHostPort(), make sure this method returns null by default
  @Nullable
  default Integer getSockHostPort(REQUEST request) {
    return sockHostPort(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSockHostPort(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Integer sockHostPort(REQUEST request) {
    return null;
  }
}
