/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import javax.annotation.Nullable;

/** An interface for getting HTTP attributes common to clients and servers. */
public interface HttpCommonAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the HTTP request method.
   *
   * <p>Examples: {@code GET}, {@code POST}, {@code HEAD}
   *
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getHttpRequestMethod(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getMethod(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  /**
   * Returns the HTTP request method.
   *
   * <p>Examples: {@code GET}, {@code POST}, {@code HEAD}
   */
  @Nullable
  default String getHttpRequestMethod(REQUEST request) {
    return getMethod(request);
  }

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   *
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getHttpRequestHeader(Object, String)} instead.
   */
  @Deprecated
  default List<String> getRequestHeader(REQUEST request, String name) {
    return emptyList();
  }

  // TODO: make this required to implement
  /**
   * Returns all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  default List<String> getHttpRequestHeader(REQUEST request, String name) {
    return getRequestHeader(request, name);
  }

  /**
   * Extracts the {@code http.status_code} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   *
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getHttpResponseStatusCode(Object, Object, Throwable)} instead.
   */
  @Deprecated
  @Nullable
  default Integer getStatusCode(REQUEST request, RESPONSE response, @Nullable Throwable error) {
    return null;
  }

  // TODO: make this required to implement
  /**
   * Returns the <a href="https://tools.ietf.org/html/rfc7231#section-6">HTTP response status
   * code</a>.
   *
   * <p>Examples: {@code 200}
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  default Integer getHttpResponseStatusCode(
      REQUEST request, RESPONSE response, @Nullable Throwable error) {
    return getStatusCode(request, response, error);
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
   * @deprecated This method is deprecated and will be removed in a future release. Implement {@link
   *     #getHttpResponseHeader(Object, Object, String)} instead.
   */
  @Deprecated
  default List<String> getResponseHeader(REQUEST request, RESPONSE response, String name) {
    return emptyList();
  }

  // TODO: make this required to implement
  /**
   * Returns all values of header named {@code name} from the response, or an empty list if there
   * were none.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  default List<String> getHttpResponseHeader(REQUEST request, RESPONSE response, String name) {
    return getResponseHeader(request, response, name);
  }
}
