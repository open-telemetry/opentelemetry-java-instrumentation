/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropagatorsInitializerTest {

  TextMapPropagator mockPreconfigured;

  @BeforeEach
  void setup() {
    mockPreconfigured = mock(TextMapPropagator.class);
  }

  @Test
  void initialize_noIdsPassedNotPreconfigured() {
    List<String> ids = emptyList();

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, TextMapPropagator::noop);

    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder("traceparent", "tracestate", "baggage");
  }

  @Test
  void initialize_noIdsPassedWithPreconfigured() {
    List<String> ids = emptyList();
    TextMapPropagator mockPropagator = mock(TextMapPropagator.class);
    when(mockPropagator.fields()).thenReturn(Collections.singleton("test"));
    Supplier<TextMapPropagator> preconfigured = () -> mockPropagator;

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, preconfigured);

    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder("traceparent", "tracestate", "baggage", "test");
  }

  @Test
  void initialize_preconfiguredSameAsId() {
    List<String> ids = singletonList("jaeger");
    Supplier<TextMapPropagator> preconfigured = () -> PropagatorsInitializer.Propagator.JAEGER;

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, preconfigured);

    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder("uber-trace-id");
  }

  @Test
  void initialize_preconfiguredDuplicatedInIds() {
    List<String> ids = Arrays.asList("b3", "jaeger", "b3");
    Supplier<TextMapPropagator> preconfigured = () -> PropagatorsInitializer.Propagator.JAEGER;

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, preconfigured);

    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder(
            "uber-trace-id", "X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled", "b3");
  }

  @Test
  void initialize_justOneId() {
    List<String> ids = singletonList("jaeger");
    Supplier<TextMapPropagator> preconfigured = TextMapPropagator::noop;

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, preconfigured);

    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder("uber-trace-id");
  }

  @Test
  void initialize_idsWithNoPreconfigured() {
    List<String> ids = Arrays.asList("b3", "unknown-but-no-harm-done", "jaeger");
    Supplier<TextMapPropagator> preconfigured = TextMapPropagator::noop;

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, preconfigured);

    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder(
            "uber-trace-id", "X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled", "b3");
  }

  @Test
  void initialize_idsAndPreconfigured() {
    List<String> ids = Arrays.asList("jaeger", "xray");
    when(mockPreconfigured.fields()).thenReturn(singletonList("mocked"));
    Supplier<TextMapPropagator> preconfigured = () -> mockPreconfigured;

    ContextPropagators propagators =
        PropagatorsInitializer.initializePropagators(ids, preconfigured);
    assertThat(propagators.getTextMapPropagator().fields())
        .containsExactlyInAnyOrder("uber-trace-id", "X-Amzn-Trace-Id", "mocked");
  }
}
