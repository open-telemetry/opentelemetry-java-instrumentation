/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

final class HttpSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  private final HttpAttributesExtractor<REQUEST, ?> attributesExtractor;

  HttpSpanNameExtractor(HttpAttributesExtractor<REQUEST, ?> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public String extract(REQUEST request) {
    String route = attributesExtractor.route(request);
    if (route != null) {
      return route;
    }
    String method = attributesExtractor.method(request);
    if (method != null) {
      return "HTTP " + method;
    }
    return "HTTP request";
  }
}
