/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.url.UrlAttributesGetter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpClientAttributesExtractor} to obtain the
 * various HTTP client attributes in a type-generic way.
 */
public interface HttpClientAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE>, UrlAttributesGetter<REQUEST> {

  /**
   * Returns the full request URL.
   *
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getFullUrl(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getUrl(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getFullUrl(REQUEST request) {
    return getUrl(request);
  }
}
