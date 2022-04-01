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
  String url(REQUEST request);

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.flavor} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  String flavor(REQUEST request, @Nullable RESPONSE response);
}
