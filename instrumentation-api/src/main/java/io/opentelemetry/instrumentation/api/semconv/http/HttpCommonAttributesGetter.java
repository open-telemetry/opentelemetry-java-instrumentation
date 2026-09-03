/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An interface for getting HTTP attributes common to clients and servers.
 *
 * @since 2.0.0
 */
public interface HttpCommonAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the HTTP request method, or {@code null} if the method is unavailable.
   *
   * <p>Examples: {@code GET}, {@code POST}, {@code HEAD}
   *
   * <p>When the method is unavailable, return {@code null} or {@value HttpConstants#_OTHER}. HTTP
   * attributes extractors record both as {@value HttpConstants#_OTHER} and do not set {@code
   * http.request.method_original}.
   */
  @Nullable
  String getHttpRequestMethod(REQUEST request);

  /**
   * Returns all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  List<String> getHttpRequestHeader(REQUEST request, String name);

  /**
   * Returns the names of all headers present on the request, or an empty iterable if they cannot be
   * enumerated.
   *
   * <p>This is only called when the configured request header selector uses wildcard patterns or
   * only excluded patterns, in which case the header names to capture cannot be known in advance.
   * Instrumentations that do not implement this method only support selectors that list header
   * names literally.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty iterable should
   * be returned instead.
   *
   * @since 2.31.0
   */
  default Iterable<String> getHttpRequestHeaderNames(REQUEST request) {
    return emptyList();
  }

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
  Integer getHttpResponseStatusCode(REQUEST request, RESPONSE response, @Nullable Throwable error);

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
  List<String> getHttpResponseHeader(REQUEST request, RESPONSE response, String name);

  /**
   * Returns the names of all headers present on the response, or an empty iterable if they cannot
   * be enumerated.
   *
   * <p>This is only called when the configured response header selector uses wildcard patterns or
   * only excluded patterns, in which case the header names to capture cannot be known in advance.
   * Instrumentations that do not implement this method only support selectors that list header
   * names literally.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty iterable should
   * be returned instead.
   *
   * @since 2.31.0
   */
  default Iterable<String> getHttpResponseHeaderNames(REQUEST request, RESPONSE response) {
    return emptyList();
  }

  /**
   * Returns a description of a class of error the operation ended with.
   *
   * <p>This method is only called if the request failed before response status code was sent or
   * received.
   *
   * <p>If this method is not implemented, or if it returns {@code null}, the exception class name
   * (if any was caught) or the value {@value HttpConstants#_OTHER} will be used as error type.
   *
   * <p>The cardinality of the error type should be low. The instrumentations implementing this
   * method are recommended to document the custom values they support.
   *
   * <p>Examples: {@code timeout}, {@code java.net.UnknownHostException}, {@code
   * server_certificate_invalid}, {@code 500}, {@code _OTHER}.
   */
  @Nullable
  default String getErrorType(
      REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
    return null;
  }
}
