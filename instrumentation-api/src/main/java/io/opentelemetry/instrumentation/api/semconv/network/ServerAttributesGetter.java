/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import javax.annotation.Nullable;

/**
 * An interface for getting attributes describing a network server.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link ServerAttributesExtractor} (or other convention
 * specific extractors) to obtain the various server attributes in a type-generic way.
 *
 * @since 2.0.0
 */
public interface ServerAttributesGetter<REQUEST> {

  /**
   * Returns the server domain name if available without reverse DNS lookup; otherwise, IP address
   * or Unix domain socket name.
   *
   * <p>Examples: {@code client.example.com}, {@code 10.1.2.80}, {@code /tmp/my.sock}
   */
  @Nullable
  default String getServerAddress(REQUEST request) {
    return null;
  }

  /**
   * Return the server port number.
   *
   * <p>Examples: {@code 80}, {@code 8080}, {@code 443}
   */
  @Nullable
  default Integer getServerPort(REQUEST request) {
    return null;
  }
}
