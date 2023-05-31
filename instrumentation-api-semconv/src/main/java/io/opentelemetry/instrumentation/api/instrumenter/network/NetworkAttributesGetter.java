/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import javax.annotation.Nullable;

/**
 * An interface for getting network attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link NetworkAttributesExtractor} (or other convention
 * specific extractors) to obtain the various network attributes in a type-generic way.
 */
public interface NetworkAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the <a href="https://osi-model.com/transport-layer/">OSI Transport Layer</a> or <a
   * href="https://en.wikipedia.org/wiki/Inter-process_communication">Inter-process Communication
   * method</a>.
   *
   * <p>Examples: {@code tcp}, {@code udp}
   */
  @Nullable
  default String getNetworkTransport(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the <a href="https://osi-model.com/network-layer/">OSI Network Layer</a> or non-OSI
   * equivalent.
   *
   * <p>Examples: {@code ipv4}, {@code ipv6}
   */
  @Nullable
  default String getNetworkType(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the <a href="https://osi-model.com/application-layer/">OSI Application Layer</a> or
   * non-OSI equivalent.
   *
   * <p>Examples: {@code ampq}, {@code http}, {@code mqtt}
   */
  @Nullable
  default String getNetworkProtocolName(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }

  /**
   * Returns the version of the application layer protocol used.
   *
   * <p>Examples: {@code 3.1.1}
   */
  @Nullable
  default String getNetworkProtocolVersion(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
