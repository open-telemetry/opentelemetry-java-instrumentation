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
    return null;
  }

  /**
   * Returns the application protocol used.
   *
   * <p>Examples: `amqp`, `http`, `mqtt`.
   */
  @Nullable
  default String getProtocolName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the version of the application protocol used.
   *
   * <p>Examples: `3.1.1`.
   */
  @Nullable
  default String getProtocolVersion(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  String getPeerName(REQUEST request);

  @Nullable
  Integer getPeerPort(REQUEST request);

  @Nullable
  default String getSockFamily(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String getSockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default String getSockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  @Nullable
  default Integer getSockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
