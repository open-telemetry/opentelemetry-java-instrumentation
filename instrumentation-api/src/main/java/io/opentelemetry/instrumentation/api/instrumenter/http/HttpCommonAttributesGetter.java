/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import javax.annotation.Nullable;

/** An interface for getting HTTP attributes common to clients and servers. */
public interface HttpCommonAttributesGetter<REQUEST, RESPONSE> {

  // Attributes that always exist in a request

  @Nullable
  String method(REQUEST request);

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  List<String> requestHeader(REQUEST request, String name);

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.request_content_length} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  Long requestContentLength(REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts the {@code http.request_content_length_uncompressed} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  Long requestContentLengthUncompressed(REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts the {@code http.status_code} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  Integer statusCode(REQUEST request, RESPONSE response);

  /**
   * Extracts the {@code http.response_content_length} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  Long responseContentLength(REQUEST request, RESPONSE response);

  /**
   * Extracts the {@code http.response_content_length_uncompressed} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  Long responseContentLengthUncompressed(REQUEST request, RESPONSE response);

  /**
   * Extracts all values of header named {@code name} from the response, or an empty list if there
   * were none.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  List<String> responseHeader(REQUEST request, RESPONSE response, String name);
}
