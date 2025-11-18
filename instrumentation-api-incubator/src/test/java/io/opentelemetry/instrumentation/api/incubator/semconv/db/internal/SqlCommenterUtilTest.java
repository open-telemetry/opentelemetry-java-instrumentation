/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.cartesian.CartesianTest;

class SqlCommenterUtilTest {
  private static final TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();

  @ParameterizedTest
  @ValueSource(strings = {"SELECT /**/ 1", "SELECT 1 --", "SELECT '/*'"})
  void skipQueriesWithComments(String query) {
    Context parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "ff01020304050600ff0a0b0c0d0e0f00",
                        "090a0b0c0d0e0f00",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));

    try (Scope ignore = parent.makeCurrent()) {
      assertThat(SqlCommenterUtil.processQuery(query, propagator, false)).isEqualTo(query);
    }
  }

  @CartesianTest
  void sqlCommenter(
      @CartesianTest.Values(booleans = {true, false}) boolean hasTraceState,
      @CartesianTest.Values(booleans = {true, false}) boolean prepend) {
    TraceState state =
        hasTraceState ? TraceState.builder().put("test", "test'").build() : TraceState.getDefault();
    Context parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "ff01020304050600ff0a0b0c0d0e0f00",
                        "090a0b0c0d0e0f00",
                        TraceFlags.getSampled(),
                        state)));

    try (Scope ignore = parent.makeCurrent()) {
      String fragment =
          hasTraceState
              ? "/*traceparent='00-ff01020304050600ff0a0b0c0d0e0f00-090a0b0c0d0e0f00-01', tracestate='test%3Dtest%27'*/"
              : "/*traceparent='00-ff01020304050600ff0a0b0c0d0e0f00-090a0b0c0d0e0f00-01'*/";
      assertThat(SqlCommenterUtil.processQuery("SELECT 1", propagator, prepend))
          .isEqualTo(prepend ? fragment + " SELECT 1" : "SELECT 1 " + fragment);
    }
  }
}
