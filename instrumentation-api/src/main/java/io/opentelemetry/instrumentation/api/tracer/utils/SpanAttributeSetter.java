/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.utils;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;

/** This helper interface allows setting attributes on both {@link Span} and {@link SpanBuilder}. */
@FunctionalInterface
public interface SpanAttributeSetter {
  <T> void setAttribute(AttributeKey<T> key, @Nullable T value);
}
