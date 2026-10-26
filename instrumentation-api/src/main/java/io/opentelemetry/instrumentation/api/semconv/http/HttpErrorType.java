/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;

import javax.annotation.Nullable;

/**
 * Helpers for classifying HTTP client and server {@code error.type} values.
 *
 * @since 3.0.0
 */
public final class HttpErrorType {

  private HttpErrorType() {}

  /**
   * Returns the {@code error.type} value for an HTTP client operation.
   *
   * <p>Error status codes for clients (400 or greater, or positive status codes below 100) take
   * precedence over exceptions. For other statuses, the exception class name is returned when
   * {@code error} is non-null. When there is no exception, a missing or non-positive status returns
   * {@value io.opentelemetry.instrumentation.api.internal.HttpConstants#_OTHER}; a successful
   * response returns {@code null}. Positive invalid status codes are classified by the client
   * status rules.
   */
  @Nullable
  public static <REQUEST, RESPONSE> String forClient(
      HttpClientAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    return getErrorType(HttpStatusCodeConverter.CLIENT, getter, request, response, error);
  }

  /**
   * Returns the {@code error.type} value for an HTTP server operation.
   *
   * <p>Error status codes for servers (500 or greater, or positive status codes below 100) take
   * precedence over exceptions. For other statuses, the exception class name is returned when
   * {@code error} is non-null. When there is no exception, a missing or non-positive status returns
   * {@value io.opentelemetry.instrumentation.api.internal.HttpConstants#_OTHER}; a successful
   * response returns {@code null}. Positive invalid status codes are classified by the server
   * status rules.
   */
  @Nullable
  public static <REQUEST, RESPONSE> String forServer(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    return getErrorType(HttpStatusCodeConverter.SERVER, getter, request, response, error);
  }

  @Nullable
  private static <REQUEST, RESPONSE> String getErrorType(
      HttpStatusCodeConverter statusCodeConverter,
      HttpCommonAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    Integer statusCode =
        response == null ? null : getter.getHttpResponseStatusCode(request, response, error);
    if (statusCode != null && statusCode > 0 && statusCodeConverter.isError(statusCode)) {
      return statusCode.toString();
    }
    if (error != null) {
      return error.getClass().getName();
    }
    if (statusCode == null || statusCode <= 0) {
      return _OTHER;
    }
    return null;
  }
}
