/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import io.opentelemetry.instrumentation.api.semconv.network.ClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.url.UrlAttributesGetter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP server attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpServerAttributesExtractor} to obtain the
 * various HTTP server attributes in a type-generic way.
 *
 * @since 2.0.0
 */
public interface HttpServerAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE>,
        UrlAttributesGetter<REQUEST>,
        NetworkAttributesGetter<REQUEST, RESPONSE>,
        ClientAttributesGetter<REQUEST> {

  /** {@inheritDoc} */
  @Nullable
  @Override
  String getUrlScheme(REQUEST request);

  /** {@inheritDoc} */
  @Nullable
  @Override
  String getUrlPath(REQUEST request);

  /** {@inheritDoc} */
  @Nullable
  @Override
  String getUrlQuery(REQUEST request);

  /**
   * Returns the matched route, that is, the path template in the format used by the respective
   * server framework.
   *
   * <p>Examples: {@code /users/:userID?}, {@code {controller}/{action}/{id?}}
   */
  @Nullable
  default String getHttpRoute(REQUEST request) {
    return null;
  }
}
