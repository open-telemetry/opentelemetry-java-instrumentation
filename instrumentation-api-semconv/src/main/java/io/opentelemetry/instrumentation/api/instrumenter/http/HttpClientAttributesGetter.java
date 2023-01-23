/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpClientAttributesExtractor} to obtain the
 * various HTTP client attributes in a type-generic way.
 */
public interface HttpClientAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE> {

  // Attributes that always exist in a request

  @Nullable
  default String getUrl(REQUEST request) {
    return url(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getUrl(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String url(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.flavor} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  default String getFlavor(REQUEST request, @Nullable RESPONSE response) {
    return flavor(request, response);
  }

  /**
   * Extracts the {@code http.flavor} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   *
   * <p>This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getFlavor(Object, Object)}.
   */
  @Deprecated
  @Nullable
  default String flavor(REQUEST request, @Nullable RESPONSE response) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }
}
