/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

/**
 * This helper interface allows setting attributes on both {@link Span} and {@link SpanBuilder}.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.api.instrumenter.Instrumenter} instead.
 */
@Deprecated
@FunctionalInterface
public interface AttributeSetter {
  <T> void setAttribute(AttributeKey<T> key, T value);
}
