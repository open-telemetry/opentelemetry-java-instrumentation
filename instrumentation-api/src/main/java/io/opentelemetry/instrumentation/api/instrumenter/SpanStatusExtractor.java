/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of {@link StatusCode}, which will be called after a request and response is completed
 * to determine its final status.
 */
@FunctionalInterface
public interface SpanStatusExtractor<REQUEST, RESPONSE> {

  /**
   * Returns the default {@link SpanStatusExtractor}, which returns {@link StatusCode#ERROR} if the
   * framework returned an unhandled exception, or {@link StatusCode#UNSET} otherwise.
   */
  @SuppressWarnings("unchecked")
  static <REQUEST, RESPONSE> SpanStatusExtractor<REQUEST, RESPONSE> getDefault() {
    return (SpanStatusExtractor<REQUEST, RESPONSE>) DefaultSpanStatusExtractor.INSTANCE;
  }

  /**
   * Returns the {@link SpanStatusExtractor} for HTTP requests, which will use the HTTP status code
   * to determine the {@link StatusCode} if available or fallback to {@linkplain #getDefault() the
   * default status} otherwise.
   */
  static <REQUEST, RESPONSE> SpanStatusExtractor<REQUEST, RESPONSE> http(
      HttpAttributesExtractor<REQUEST, RESPONSE> attributesExtractor) {
    return new HttpSpanStatusExtractor<>(attributesExtractor);
  }

  /** Returns the {@link StatusCode}. */
  StatusCode extract(REQUEST request, RESPONSE response, @Nullable Throwable error);
}
