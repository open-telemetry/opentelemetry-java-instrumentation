/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of the <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#name">HTTP
 * span name</a>. Instrumentation of HTTP server or client frameworks should use this class to
 * comply with OpenTelemetry HTTP semantic conventions.
 */
public final class HttpSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} which should be used for HTTP requests. HTTP attributes
   * will be examined to determine the name of the span.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      HttpCommonAttributesExtractor<REQUEST, ?> attributesExtractor) {
    return new HttpSpanNameExtractor<>(attributesExtractor);
  }

  private final HttpCommonAttributesExtractor<REQUEST, ?> attributesExtractor;

  private HttpSpanNameExtractor(HttpCommonAttributesExtractor<REQUEST, ?> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public String extract(REQUEST request) {
    String route = extractRoute(request);
    if (route != null) {
      return route;
    }
    String method = attributesExtractor.method(request);
    if (method != null) {
      return "HTTP " + method;
    }
    return "HTTP request";
  }

  @Nullable
  private String extractRoute(REQUEST request) {
    if (attributesExtractor instanceof HttpServerAttributesExtractor) {
      return ((HttpServerAttributesExtractor<REQUEST, ?>) attributesExtractor).route(request);
    }
    return null;
  }
}
