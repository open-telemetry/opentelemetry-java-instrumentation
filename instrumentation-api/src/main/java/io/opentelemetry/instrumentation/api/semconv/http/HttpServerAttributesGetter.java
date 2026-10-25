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

  /**
   * Returns the final {@code error.type} value for the HTTP server operation.
   *
   * <p>The default implementation returns error response status codes for servers (500 or greater,
   * or positive status codes below 100), the exception class name when {@code error} is non-null,
   * or {@value io.opentelemetry.instrumentation.api.internal.HttpConstants#_OTHER} when the status
   * is missing or non-positive and there is no exception. It omits {@code error.type} for a
   * successful response without an exception. Positive invalid status codes are classified by the
   * server status rules.
   *
   * <p>An override returning {@code null} omits the attribute, including for an error response
   * status. To retain the default classification from an override, delegate to {@link
   * HttpErrorType#forServer(HttpCommonAttributesGetter, Object, Object, Throwable)}.
   */
  @Nullable
  @Override
  default String getErrorType(
      REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
    return HttpErrorType.forServer(this, request, response, error);
  }
}
