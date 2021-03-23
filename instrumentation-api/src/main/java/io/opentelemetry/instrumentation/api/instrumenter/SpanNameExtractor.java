/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

@FunctionalInterface
public interface SpanNameExtractor<REQUEST> {

  static <REQUEST> SpanNameExtractor<REQUEST> http(
      HttpAttributesExtractor<REQUEST, ?> attributesExtractor) {
    return new HttpSpanNameExtractor<>(attributesExtractor);
  }

  String extract(REQUEST request);
}
