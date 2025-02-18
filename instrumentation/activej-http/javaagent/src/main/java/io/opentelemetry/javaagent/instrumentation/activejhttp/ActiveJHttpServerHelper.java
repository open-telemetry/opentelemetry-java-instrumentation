/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import io.activej.http.HttpError;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import java.util.Map;

/**
 * This utility class provides helper methods for creating and manipulating HTTP responses in the
 * context of ActiveJ HTTP server instrumentation. It is designed to simplify the process of
 * constructing responses with trace context propagation, particularly for error handling and
 * distributed tracing.
 *
 * @author Krishna Chaitanya Surapaneni
 */
public final class ActiveJHttpServerHelper {

  private ActiveJHttpServerHelper() {
    throw new UnsupportedOperationException();
  }

  /**
   * Creates an HTTP response with trace context propagation. This method constructs a response
   * based on the provided exception, traceparent header, and optional existing HTTP response. It
   * ensures that the response includes the {@code traceparent} header for distributed tracing.
   *
   * <p>The response is constructed as follows:
   *
   * <ul>
   *   <li>If an existing response is provided, its status code, body, and headers are preserved.
   *   <li>If no existing response is provided but an exception is available, the status code is
   *       derived from the exception (e.g., HTTP error codes).
   *   <li>If neither a response nor an exception is provided, a default status code of 500
   *       (Internal Server Error) is used.
   *   <li>The {@code traceparent} header is added to the response to propagate trace context.
   * </ul>
   *
   * @param throwable The exception associated with the response, if any. Used to determine the
   *     status code and message.
   * @param traceparent The traceparent header value for distributed tracing.
   * @param httpResponse The existing HTTP response, if available. Used as the basis for
   *     constructing the new response.
   * @return A new {@code HttpResponse} instance with the specified trace context and other
   *     attributes.
   */
  public static HttpResponse createResponse(
      Throwable throwable, String traceparent, HttpResponse httpResponse) {
    int code = 500;
    if (httpResponse != null) {
      code = httpResponse.getCode();
    } else if (throwable instanceof HttpError) {
      HttpError error = (HttpError) throwable;
      code = error.getCode();
    }
    HttpResponse.Builder responseBuilder = HttpResponse.ofCode(code);
    if (httpResponse != null) {
      if (httpResponse.hasBody()) {
        responseBuilder.withBody(httpResponse.getBody());
      }
      for (Map.Entry<HttpHeader, HttpHeaderValue> entry : httpResponse.getHeaders()) {
        responseBuilder.withHeader(entry.getKey(), entry.getValue());
      }
    }
    if (throwable != null) {
      responseBuilder.withPlainText(throwable.getMessage());
    }
    return responseBuilder.withHeader(HttpHeaders.of("traceparent"), traceparent).build();
  }
}
