/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpClientAttributesExtractor} to obtain the
 * various HTTP client attributes in a type-generic way.
 *
 * @since 2.0.0
 */
public interface HttpClientAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE>,
        NetworkAttributesGetter<REQUEST, RESPONSE>,
        ServerAttributesGetter<REQUEST> {

  /**
   * Returns the absolute URL describing a network resource according to <a
   * href="https://www.rfc-editor.org/rfc/rfc3986">RFC3986</a>.
   *
   * <p>Examples: {@code https://www.foo.bar/search?q=OpenTelemetry#SemConv}; {@code //localhost}
   */
  @Nullable
  String getUrlFull(REQUEST request);

  /** {@inheritDoc} */
  @Nullable
  @Override
  String getServerAddress(REQUEST request);

  /** {@inheritDoc} */
  @Nullable
  @Override
  Integer getServerPort(REQUEST request);

  /**
   * Returns the {@code error.type} value for the HTTP client operation.
   *
   * <p>The default implementation returns error response status codes for clients (400 or greater,
   * or positive status codes below 100), the exception class name when {@code error} is non-null,
   * or {@value io.opentelemetry.instrumentation.api.internal.HttpConstants#_OTHER} when the status
   * is missing or non-positive and there is no exception. It omits {@code error.type} for a
   * successful response without an exception. Positive invalid status codes are classified by the
   * client status rules.
   *
   * <p>Use {@link HttpErrorType#forClient(HttpCommonAttributesGetter, Object, Object, Throwable)}
   * to apply the default client classification.
   */
  @Nullable
  @Override
  default String getErrorType(
      REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
    return HttpErrorType.forClient(this, request, response, error);
  }
}
