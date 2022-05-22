/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import javax.annotation.Nullable;

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
      HttpCommonAttributesGetter<REQUEST, ?> getter) {
    return new HttpSpanNameExtractor<>(getter);
  }

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;

  private HttpSpanNameExtractor(HttpCommonAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String route = extractRoute(request);
    if (route != null) {
      return route;
    }
    String method = getter.method(request);
    if (method != null) {
      return "HTTP " + method;
    }
    return "HTTP request";
  }

  @Nullable
  private String extractRoute(REQUEST request) {
    if (getter instanceof HttpServerAttributesGetter) {
      return ((HttpServerAttributesGetter<REQUEST, ?>) getter).route(request);
    }
    return null;
  }
}
