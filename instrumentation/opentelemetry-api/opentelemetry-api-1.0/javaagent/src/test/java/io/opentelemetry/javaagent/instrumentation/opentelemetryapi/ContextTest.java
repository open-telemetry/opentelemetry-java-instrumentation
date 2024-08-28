/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(AgentInstrumentationExtension.class)
public class ContextTest {

  @Test
  @DisplayName("Span.current() should return invalid")
  public void spanCurrentShouldReturnInvalid() {
    // When
    Span span = Span.current();

    // Then
    assertFalse(span.getSpanContext().isValid());
  }

  @Test
  @DisplayName("Span.current() should return span")
  public void spanCurrentShouldReturnSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Span span = Span.current();

      // Then
      assertEquals(testSpan, span);
    }
  }

  @Test
  @DisplayName("Span.fromContext should return invalid")
  public void spanFromContextShouldReturnInvalid() {
    // When
    Span span = Span.fromContext(Context.current());

    // Then
    assertFalse(span.getSpanContext().isValid());
  }

  @Test
  @DisplayName("getSpan should return span")
  public void getSpanShouldReturnSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Span span = Span.fromContext(Context.current());

      // Then
      assertEquals(testSpan, span);
    }
  }

  @Test
  @DisplayName("Span.fromContextOrNull should return null")
  public void spanFromContextOrNullShouldReturnNull() {
    // When
    Span span = Span.fromContextOrNull(Context.current());

    // Then
    assertNull(span);
  }

  @Test
  @DisplayName("Span.fromContextOrNull should return span")
  public void spanFromContextOrNullShouldReturnSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Span span = Span.fromContextOrNull(Context.current());

      // Then
      assertEquals(testSpan, span);
    }
  }
}
