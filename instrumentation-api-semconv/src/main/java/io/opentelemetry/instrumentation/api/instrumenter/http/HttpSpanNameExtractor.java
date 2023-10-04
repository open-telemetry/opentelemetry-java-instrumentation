/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Extractor of the <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-spans.md#name">HTTP
 * span name</a>. Instrumentation of HTTP server or client frameworks should use this class to
 * comply with OpenTelemetry HTTP semantic conventions.
 */
public final class HttpSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} which should be used for HTTP requests with default
   * configuration. HTTP attributes will be examined to determine the name of the span.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      HttpCommonAttributesGetter<REQUEST, ?> getter) {
    return builder(getter).build();
  }

  /**
   * Returns a new {@link HttpSpanNameExtractorBuilder} that can be used to configure the HTTP span
   * name extractor.
   */
  public static <REQUEST> HttpSpanNameExtractorBuilder<REQUEST> builder(
      HttpCommonAttributesGetter<REQUEST, ?> getter) {
    return new HttpSpanNameExtractorBuilder<>(getter);
  }

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;
  private final Set<String> knownMethods;

  HttpSpanNameExtractor(HttpSpanNameExtractorBuilder<REQUEST> builder) {
    this.getter = builder.httpAttributesGetter;
    this.knownMethods = new HashSet<>(builder.knownMethods);
  }

  @Override
  public String extract(REQUEST request) {
    String method = getter.getHttpRequestMethod(request);
    String route = extractRoute(request);
    if (method != null) {
      if (!knownMethods.contains(method)) {
        method = "HTTP";
      }
      return route == null ? method : method + " " + route;
    }
    return "HTTP";
  }

  @Nullable
  private String extractRoute(REQUEST request) {
    if (getter instanceof HttpServerAttributesGetter) {
      return ((HttpServerAttributesGetter<REQUEST, ?>) getter).getHttpRoute(request);
    }
    return null;
  }
}
