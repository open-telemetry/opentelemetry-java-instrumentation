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
  default String getMethod(REQUEST request) {
    return method(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getMethod(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String method(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  default List<String> getRequestHeader(REQUEST request, String name) {
    return requestHeader(request, name);
  }

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   *
   * <p>This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getRequestHeader(Object, String)} instead.
   */
  @Deprecated
  default List<String> requestHeader(REQUEST request, String name) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.status_code} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  default Integer getStatusCode(REQUEST request, RESPONSE response, @Nullable Throwable error) {
    return statusCode(request, response, error);
  }

  /**
   * Extracts the {@code http.status_code} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   *
   * <p>This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getStatusCode(Object, Object, Throwable)} instead.
   */
  @Deprecated
  @Nullable
  default Integer statusCode(REQUEST request, RESPONSE response, @Nullable Throwable error) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

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
  default List<String> getResponseHeader(REQUEST request, RESPONSE response, String name) {
    return responseHeader(request, response, name);
  }

  /**
   * Extracts all values of header named {@code name} from the response, or an empty list if there
   * were none.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   *
   * <p>This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getResponseHeader(Object, Object, String)} instead.
   */
  @Deprecated
  default List<String> responseHeader(REQUEST request, RESPONSE response, String name) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }
}
