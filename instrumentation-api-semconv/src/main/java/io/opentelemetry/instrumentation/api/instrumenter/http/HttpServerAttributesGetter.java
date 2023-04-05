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
  String getFlavor(REQUEST request);

  @Nullable
  String getTarget(REQUEST request);

  @Nullable
  default String getRoute(REQUEST request) {
    return null;
  }

  @Nullable
  String getScheme(REQUEST request);
}
