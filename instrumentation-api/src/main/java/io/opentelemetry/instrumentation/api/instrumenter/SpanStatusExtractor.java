/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;
import javax.annotation.Nullable;

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

  /** Returns the {@link StatusCode}. */
  StatusCode extract(REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error);
}
