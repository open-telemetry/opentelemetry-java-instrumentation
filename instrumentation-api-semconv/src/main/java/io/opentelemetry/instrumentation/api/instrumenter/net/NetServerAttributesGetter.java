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
    return null;
  }

  /**
   * Returns the application protocol used.
   *
   * <p>Examples: `amqp`, `http`, `mqtt`.
   */
  @Nullable
  default String getProtocolName(REQUEST request) {
    return null;
  }

  /**
   * Returns the version of the application protocol used.
   *
   * <p>Examples: `3.1.1`.
   */
  @Nullable
  default String getProtocolVersion(REQUEST request) {
    return null;
  }

  @Nullable
  String getHostName(REQUEST request);

  @Nullable
  Integer getHostPort(REQUEST request);

  @Nullable
  default String getSockFamily(REQUEST request) {
    return null;
  }

  @Nullable
  default String getSockPeerAddr(REQUEST request) {
    return null;
  }

  @Nullable
  default Integer getSockPeerPort(REQUEST request) {
    return null;
  }

  @Nullable
  default String getSockHostAddr(REQUEST request) {
    return null;
  }

  @Nullable
  default Integer getSockHostPort(REQUEST request) {
    return null;
  }
}
