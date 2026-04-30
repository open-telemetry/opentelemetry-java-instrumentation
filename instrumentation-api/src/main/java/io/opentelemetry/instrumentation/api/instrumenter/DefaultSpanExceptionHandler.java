/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;

enum DefaultSpanExceptionHandler implements SpanExceptionHandler {
  INSTANCE;

  @Override
  public void handle(Span span, Throwable throwable) {
    span.recordException(throwable);
  }
}
