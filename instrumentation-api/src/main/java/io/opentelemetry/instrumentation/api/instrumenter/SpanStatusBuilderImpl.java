/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

final class SpanStatusBuilderImpl implements SpanStatusBuilder {
  private final Span span;

  SpanStatusBuilderImpl(Span span) {
    this.span = span;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanStatusBuilder setStatus(StatusCode statusCode, String description) {
    span.setStatus(statusCode, description);
    return this;
  }
}
