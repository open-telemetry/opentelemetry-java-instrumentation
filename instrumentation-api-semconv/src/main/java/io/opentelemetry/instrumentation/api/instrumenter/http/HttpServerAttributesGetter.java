/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.url.UrlAttributesGetter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP server attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpServerAttributesExtractor} to obtain the
 * various HTTP server attributes in a type-generic way.
 */
public interface HttpServerAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE>, UrlAttributesGetter<REQUEST> {

  /**
   * Returns the URI scheme.
   *
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getUrlScheme(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getScheme(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getUrlScheme(REQUEST request) {
    return getScheme(request);
  }

  /**
   * Returns the path and query pieces of the URL, joined by the {@code ?} character.
   *
   * @deprecated This method is deprecated and will be removed in the following release. Implement
   *     {@link #getUrlPath(Object)} and {@link #getUrlQuery(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getTarget(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getUrlPath(REQUEST request) {
    String target = getTarget(request);
    if (target == null) {
      return null;
    }
    int separatorPos = target.indexOf('?');
    return separatorPos == -1 ? target : target.substring(0, separatorPos);
  }

  // TODO: make this required to implement
  /** {@inheritDoc} */
  @Nullable
  @Override
  default String getUrlQuery(REQUEST request) {
    String target = getTarget(request);
    if (target == null) {
      return null;
    }
    int separatorPos = target.indexOf('?');
    return separatorPos == -1 ? null : target.substring(separatorPos + 1);
  }

  /**
   * Returns the matched route (path template in the format used by the respective server
   * framework).
   *
   * <p>Examples: {@code /users/:userID?}, {@code {controller}/{action}/{id?}}
   *
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getHttpRoute(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getRoute(REQUEST request) {
    return null;
  }

  /**
   * Returns the matched route (path template in the format used by the respective server
   * framework).
   *
   * <p>Examples: {@code /users/:userID?}, {@code {controller}/{action}/{id?}}
   */
  @Nullable
  default String getHttpRoute(REQUEST request) {
    return getRoute(request);
  }
}
