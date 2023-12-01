/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.Set;

/**
 * Extractor of the <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#name">HTTP
 * span name</a>.
 *
 * @since 2.0.0
 */
public final class HttpSpanNameExtractor {

  /**
   * Returns an HTTP client {@link SpanNameExtractor} with default configuration.
   *
   * @see Instrumenter#builder(OpenTelemetry, String, SpanNameExtractor)
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      HttpClientAttributesGetter<REQUEST, ?> getter) {
    return builder(getter).build();
  }

  /**
   * Returns an HTTP server {@link SpanNameExtractor} with default configuration.
   *
   * @see Instrumenter#builder(OpenTelemetry, String, SpanNameExtractor)
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      HttpServerAttributesGetter<REQUEST, ?> getter) {
    return builder(getter).build();
  }

  /**
   * Returns a new {@link HttpSpanNameExtractorBuilder} that can be used to configure the HTTP
   * client span name extractor.
   */
  public static <REQUEST> HttpSpanNameExtractorBuilder<REQUEST> builder(
      HttpClientAttributesGetter<REQUEST, ?> getter) {
    return new HttpSpanNameExtractorBuilder<>(getter, null);
  }

  /**
   * Returns a new {@link HttpSpanNameExtractorBuilder} that can be used to configure the HTTP
   * server span name extractor.
   */
  public static <REQUEST> HttpSpanNameExtractorBuilder<REQUEST> builder(
      HttpServerAttributesGetter<REQUEST, ?> getter) {
    return new HttpSpanNameExtractorBuilder<>(null, getter);
  }

  static final class Client<REQUEST> implements SpanNameExtractor<REQUEST> {

    private final HttpClientAttributesGetter<REQUEST, ?> getter;
    private final Set<String> knownMethods;

    Client(HttpClientAttributesGetter<REQUEST, ?> getter, Set<String> knownMethods) {
      this.getter = getter;
      this.knownMethods = knownMethods;
    }

    @Override
    public String extract(REQUEST request) {
      String method = getter.getHttpRequestMethod(request);
      if (method == null || !knownMethods.contains(method)) {
        return "HTTP";
      }
      return method;
    }
  }

  static final class Server<REQUEST> implements SpanNameExtractor<REQUEST> {

    private final HttpServerAttributesGetter<REQUEST, ?> getter;
    private final Set<String> knownMethods;

    Server(HttpServerAttributesGetter<REQUEST, ?> getter, Set<String> knownMethods) {
      this.getter = getter;
      this.knownMethods = knownMethods;
    }

    @Override
    public String extract(REQUEST request) {
      String method = getter.getHttpRequestMethod(request);
      String route = getter.getHttpRoute(request);
      if (method == null) {
        return "HTTP";
      }
      if (!knownMethods.contains(method)) {
        method = "HTTP";
      }
      return route == null ? method : method + " " + route;
    }
  }

  private HttpSpanNameExtractor() {}
}
