/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import javax.annotation.Nullable;

/**
 * An interface for getting HTTP server attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpServerAttributesExtractor} to obtain the
 * various HTTP server attributes in a type-generic way.
 */
public interface HttpServerAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE> {

  // Attributes that always exist in a request

  @Nullable
  String flavor(REQUEST request);

  @Nullable
  String target(REQUEST request);

  @Nullable
  String route(REQUEST request);

  @Nullable
  String scheme(REQUEST request);

  /**
   * The primary server name of the matched virtual host. This should be obtained via configuration,
   * not from the Host header. If no such configuration can be obtained, this method should return
   * {@code null}.
   */
  @Nullable
  String serverName(REQUEST request);
}
