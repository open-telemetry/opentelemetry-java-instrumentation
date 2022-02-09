/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of the <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#status">HTTP
 * span status</a>. Instrumentation of HTTP server or client frameworks should use this class to
 * comply with OpenTelemetry HTTP semantic conventions.
 */
public final class HttpSpanStatusExtractor<REQUEST, RESPONSE>
    implements SpanStatusExtractor<REQUEST, RESPONSE> {

  /**
   * Returns the {@link SpanStatusExtractor} for HTTP requests, which will use the HTTP status code
   * to determine the {@link StatusCode} if available or fallback to {@linkplain #getDefault() the
   * default status} otherwise.
   */
  public static <REQUEST, RESPONSE> SpanStatusExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<? super REQUEST, ? super RESPONSE> getter) {
    return new HttpSpanStatusExtractor<>(getter, HttpStatusConverter.CLIENT);
  }

  /**
   * Returns the {@link SpanStatusExtractor} for HTTP requests, which will use the HTTP status code
   * to determine the {@link StatusCode} if available or fallback to {@linkplain #getDefault() the
   * default status} otherwise.
   */
  public static <REQUEST, RESPONSE> SpanStatusExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<? super REQUEST, ? super RESPONSE> getter) {
    return new HttpSpanStatusExtractor<>(getter, HttpStatusConverter.SERVER);
  }

  private final HttpCommonAttributesGetter<? super REQUEST, ? super RESPONSE> getter;
  private final HttpStatusConverter statusConverter;

  private HttpSpanStatusExtractor(
      HttpCommonAttributesGetter<? super REQUEST, ? super RESPONSE> getter,
      HttpStatusConverter statusConverter) {
    this.getter = getter;
    this.statusConverter = statusConverter;
  }

  @Override
  public StatusCode extract(REQUEST request, @Nullable RESPONSE response, Throwable error) {
    if (response != null) {
      Integer statusCode = getter.statusCode(request, response);
      if (statusCode != null) {
        StatusCode statusCodeObj = statusConverter.statusFromHttpStatus(statusCode);
        if (statusCodeObj == StatusCode.ERROR) {
          return statusCodeObj;
        }
      }
    }
    return SpanStatusExtractor.getDefault().extract(request, response, error);
  }
}
