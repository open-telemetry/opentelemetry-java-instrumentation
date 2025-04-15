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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlCommenterUtilTest {

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
      assertThat(SqlCommenterUtil.processQuery(query)).isEqualTo(query);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void sqlCommenter(boolean hasTraceState) {
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
      assertThat(SqlCommenterUtil.processQuery("SELECT 1"))
          .isEqualTo(
              hasTraceState
                  ? "SELECT 1 /*traceparent='00-ff01020304050600ff0a0b0c0d0e0f00-090a0b0c0d0e0f00-01', tracestate='test%3Dtest%27'*/"
                  : "SELECT 1 /*traceparent='00-ff01020304050600ff0a0b0c0d0e0f00-090a0b0c0d0e0f00-01'*/");
    }
  }
}
