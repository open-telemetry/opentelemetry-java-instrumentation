/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.HashSet;
import java.util.Set;
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
      HttpCommonAttributesGetter<REQUEST, ?> getter, Set<String> knownMethods) {
    return new HttpSpanNameExtractor<>(getter, knownMethods);
  }

  @Deprecated
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      HttpCommonAttributesGetter<REQUEST, ?> getter) {
    return new HttpSpanNameExtractor<>(getter, HttpConstants.KNOWN_METHODS);
  }

  private final HttpCommonAttributesGetter<REQUEST, ?> getter;
  private final Set<String> knownMethods;

  private HttpSpanNameExtractor(
      HttpCommonAttributesGetter<REQUEST, ?> getter, Set<String> knownMethods) {
    this.getter = getter;
    this.knownMethods = new HashSet<>(knownMethods);
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
