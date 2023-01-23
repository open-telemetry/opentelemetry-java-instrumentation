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
  default String getFlavor(REQUEST request) {
    return flavor(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getFlavor(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String flavor(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getTarget(REQUEST request) {
    return target(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getTarget(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String target(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getRoute(REQUEST request) {
    return route(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getRoute(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String route(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getScheme(REQUEST request) {
    return scheme(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getScheme(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String scheme(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }
}
