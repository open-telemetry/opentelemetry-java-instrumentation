/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;

/**
 * Writes an exception event to a {@link Span}. Can be used to customize how exception telemetry is
 * recorded, for example to sanitize sensitive data from exception messages before they appear in
 * spans.
 */
@FunctionalInterface
public interface SpanExceptionHandler {

  /** Records the given {@code throwable} as an exception event on the {@code span}. */
  void handle(Span span, Throwable throwable);

  /**
   * Returns the default {@link SpanExceptionHandler}, which delegates to {@link
   * Span#recordException(Throwable)}.
   */
  static SpanExceptionHandler getDefault() {
    return DefaultSpanExceptionHandler.INSTANCE;
  }
}
