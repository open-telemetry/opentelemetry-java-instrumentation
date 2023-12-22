/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import javax.annotation.Nullable;

/**
 * An interface for getting attributes describing a network client.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link ClientAttributesExtractor} (or other convention
 * specific extractors) to obtain the various server attributes in a type-generic way.
 *
 * @since 2.0.0
 */
public interface ClientAttributesGetter<REQUEST> {

  /**
   * Returns the client address - domain name if available without reverse DNS lookup; otherwise, IP
   * address or Unix domain socket name.
   *
   * <p>Examples: {@code client.example.com}, {@code 10.1.2.80}, {@code /tmp/my.sock}
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
}
