/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.SpanKind;

@FunctionalInterface
public interface SpanKindExtractor<REQUEST> {

  static <REQUEST> SpanKindExtractor<REQUEST> alwaysInternal() {
    return request -> SpanKind.INTERNAL;
  }

  static <REQUEST> SpanKindExtractor<REQUEST> alwaysClient() {
    return request -> SpanKind.CLIENT;
  }

  static <REQUEST> SpanKindExtractor<REQUEST> alwaysServer() {
    return request -> SpanKind.SERVER;
  }

  SpanKind extract(REQUEST request);
}
