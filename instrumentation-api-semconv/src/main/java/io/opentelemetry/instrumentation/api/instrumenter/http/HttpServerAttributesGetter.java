/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.url.UrlAttributesGetter;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP server attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link HttpServerAttributesExtractor} to obtain the
 * various HTTP server attributes in a type-generic way.
 */
@SuppressWarnings(
    "deprecation") // implementing the NetServerAttributesGetter for the old->stable semconv story;
// will be removed in 2.0
public interface HttpServerAttributesGetter<REQUEST, RESPONSE>
    extends HttpCommonAttributesGetter<REQUEST, RESPONSE>,
        UrlAttributesGetter<REQUEST>,
        io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter<
            REQUEST, RESPONSE>,
        NetworkAttributesGetter<REQUEST, RESPONSE>,
        ServerAttributesGetter<REQUEST, RESPONSE>,
        ClientAttributesGetter<REQUEST, RESPONSE> {

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
   * Returns the matched route (path template in the format used by the respective server
   * framework).
   *
   * <p>Examples: {@code /users/:userID?}, {@code {controller}/{action}/{id?}}
   */
  @Nullable
  default String getHttpRoute(REQUEST request) {
    return null;
  }
}
