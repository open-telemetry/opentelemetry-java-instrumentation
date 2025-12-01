/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.ShouldStartFilter;
import org.junit.jupiter.api.Test;

class ShouldStartFilterTest {

  @Test
  void none() {
    ShouldStartFilter<String> filter = ShouldStartFilter.none();

    assertThat(filter.shouldStart(Context.root(), "request", SpanKind.CLIENT, "test")).isTrue();
    assertThat(filter.getPriority()).isEqualTo(0);
  }

  @Test
  void allOf() {
    ShouldStartFilter<String> filter1 =
        (context, request, spanKind, instrumentationName) -> !request.equals("blocked1");
    ShouldStartFilter<String> filter2 =
        (context, request, spanKind, instrumentationName) -> !request.equals("blocked2");

    ShouldStartFilter<String> compositeFilter = ShouldStartFilter.allOf(asList(filter1, filter2));

    assertThat(compositeFilter.shouldStart(Context.root(), "allowed", SpanKind.CLIENT, "test"))
        .isTrue();
    assertThat(compositeFilter.shouldStart(Context.root(), "blocked1", SpanKind.CLIENT, "test"))
        .isFalse();
    assertThat(compositeFilter.shouldStart(Context.root(), "blocked2", SpanKind.CLIENT, "test"))
        .isFalse();
  }

  @Test
  void allOfPriority() {
    StringBuilder executionOrder = new StringBuilder();

    ShouldStartFilter<String> highPriority =
        new ShouldStartFilter<String>() {
          @Override
          public boolean shouldStart(
              Context parentContext,
              String request,
              SpanKind spanKind,
              String instrumentationName) {
            executionOrder.append("high");
            return false;
          }

          @Override
          public int getPriority() {
            return 1;
          }
        };

    ShouldStartFilter<String> lowPriority =
        new ShouldStartFilter<String>() {
          @Override
          public boolean shouldStart(
              Context parentContext,
              String request,
              SpanKind spanKind,
              String instrumentationName) {
            executionOrder.append("low");
            return true;
          }

          @Override
          public int getPriority() {
            return 10;
          }
        };

    ShouldStartFilter<String> compositeFilter =
        ShouldStartFilter.allOf(asList(lowPriority, highPriority));
    boolean result =
        compositeFilter.shouldStart(Context.root(), "request", SpanKind.CLIENT, "test");

    assertThat(result).isFalse();
    assertThat(executionOrder.toString()).isEqualTo("high");
  }
}
