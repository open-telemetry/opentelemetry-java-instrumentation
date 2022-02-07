/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
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
  String flavor(REQUEST request);

  @Nullable
  String target(REQUEST request);

  @Nullable
  String route(REQUEST request);

  @Nullable
  String scheme(REQUEST request);

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.server_name} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  String serverName(REQUEST request, @Nullable RESPONSE response);
}
