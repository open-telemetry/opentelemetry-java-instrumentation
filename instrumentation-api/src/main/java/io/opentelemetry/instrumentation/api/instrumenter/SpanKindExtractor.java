/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.SpanKind;

/**
 * Extractor of the {@link SpanKind}. In most cases, the span kind will be constant for server or
 * client requests, but you may need to implement a custom {@link SpanKindExtractor} if a framework
 * can generate different kinds of spans, for example both HTTP and messaging spans.
 */
@FunctionalInterface
public interface SpanKindExtractor<REQUEST> {

  /** Returns the {@link SpanKind} corresponding to the {@link REQUEST}. */
  SpanKind extract(REQUEST request);

  /** Returns a {@link SpanNameExtractor} which always returns {@link SpanKind#INTERNAL}. */
  static <REQUEST> SpanKindExtractor<REQUEST> alwaysInternal() {
    return request -> SpanKind.INTERNAL;
  }

  /** Returns a {@link SpanNameExtractor} which always returns {@link SpanKind#CLIENT}. */
  static <REQUEST> SpanKindExtractor<REQUEST> alwaysClient() {
    return request -> SpanKind.CLIENT;
  }

  /** Returns a {@link SpanNameExtractor} which always returns {@link SpanKind#SERVER}. */
  static <REQUEST> SpanKindExtractor<REQUEST> alwaysServer() {
    return request -> SpanKind.SERVER;
  }

  /** Returns a {@link SpanNameExtractor} which always returns {@link SpanKind#PRODUCER}. */
  static <REQUEST> SpanKindExtractor<REQUEST> alwaysProducer() {
    return request -> SpanKind.PRODUCER;
  }

  /** Returns a {@link SpanNameExtractor} which always returns {@link SpanKind#CONSUMER}. */
  static <REQUEST> SpanKindExtractor<REQUEST> alwaysConsumer() {
    return request -> SpanKind.CONSUMER;
  }
}
