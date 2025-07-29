/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextTest {

  @Test
  @DisplayName("Span.current() should return invalid")
  void spanCurrentShouldReturnInvalid() {
    // When
    Span span = Span.current();
    // Then
    assertThat(span.getSpanContext().isValid()).isFalse();
  }

  @Test
  @DisplayName("Span.current() should return span")
  void spanCurrentShouldReturnSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Span span = Span.current();

      // Then
      assertThat(span).isEqualTo(testSpan);
    }
  }

  @Test
  @DisplayName("Span.fromContext should return invalid")
  void spanFromContextShouldReturnInvalid() {
    // When
    Span span = Span.fromContext(Context.current());

    // Then
    assertThat(span.getSpanContext().isValid()).isFalse();
  }

  @Test
  @DisplayName("getSpan should return span")
  void getSpanShouldReturnSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Span span = Span.fromContext(Context.current());

      // Then
      assertThat(span).isEqualTo(testSpan);
    }
  }

  @Test
  @DisplayName("Span.fromContextOrNull should return null")
  void spanFromContextOrNullShouldReturnNull() {
    // When
    Span span = Span.fromContextOrNull(Context.current());

    // Then
    assertThat(span).isNull();
  }

  @Test
  @DisplayName("Span.fromContextOrNull should return span")
  void spanFromContextOrNullShouldReturnSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Span span = Span.fromContextOrNull(Context.current());

      // Then
      assertThat(span).isEqualTo(testSpan);
    }
  }
}
