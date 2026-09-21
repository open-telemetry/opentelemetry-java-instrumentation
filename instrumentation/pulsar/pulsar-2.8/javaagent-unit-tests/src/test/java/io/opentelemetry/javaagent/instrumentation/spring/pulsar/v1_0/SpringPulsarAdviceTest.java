/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.VirtualFieldStore;
import io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0.DefaultPulsarMessageListenerContainerInstrumentation.DispatchMessageToListenerAdvice;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collection;
import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

@EnabledForJreRange(min = JRE.JAVA_17)
class SpringPulsarAdviceTest {
  private static final SpanContext PRODUCER_CONTEXT =
      SpanContext.createFromRemoteParent(
          "0123456789abcdef0123456789abcdef",
          "0123456789abcdef",
          TraceFlags.getSampled(),
          TraceState.getDefault());
  private static InMemoryMetricReader metricReader;
  private static InMemorySpanExporter spanExporter;
  private static OpenTelemetrySdk openTelemetry;

  @BeforeAll
  static void setUp() {
    metricReader = InMemoryMetricReader.createDelta();
    spanExporter = InMemorySpanExporter.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    openTelemetry =
        OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @BeforeEach
  void resetTelemetry() {
    metricReader.collectAllMetrics();
    spanExporter.reset();
  }

  @AfterAll
  static void tearDown() {
    openTelemetry.close();
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void processingPreservesParentAndProducerLinkAfterFailure() {
    assumeTrue(emitStableMessagingSemconv());
    Message<?> message = message("test-topic");
    Span parent = openTelemetry.getTracer("test").spanBuilder("delivery-parent").startSpan();
    Context parentContext = Context.root().with(parent);

    VirtualFieldStore.setProcessParentContext(message, parentContext);

    runProcessing(message, new IllegalStateException("processing failed"));

    runProcessing(message, null);
    parent.end();

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertProcessDuration(metrics, 2);
    assertThat(spanExporter.getFinishedSpanItems())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.spring-pulsar-1.0"))
        .hasSize(2)
        .allSatisfy(
            span -> {
              assertThat(span.getParentSpanId()).isEqualTo(parent.getSpanContext().getSpanId());
              assertThat(span.getLinks())
                  .singleElement()
                  .satisfies(link -> assertThat(link.getSpanContext()).isEqualTo(PRODUCER_CONTEXT));
            });
  }

  @Test
  void completionKeepsCapturedProcessParent() {
    assumeTrue(emitStableMessagingSemconv());
    Message<?> message = message("test-topic");
    Span firstParent = openTelemetry.getTracer("test").spanBuilder("first-parent").startSpan();
    Span secondParent = openTelemetry.getTracer("test").spanBuilder("second-parent").startSpan();
    VirtualFieldStore.setProcessParentContext(message, Context.root().with(firstParent));

    DispatchMessageToListenerAdvice.AdviceScope first =
        DispatchMessageToListenerAdvice.onEnter(message);
    assertThat(first).isNotNull();
    VirtualFieldStore.setProcessParentContext(message, Context.root().with(secondParent));
    DispatchMessageToListenerAdvice.onExit(null, first);

    runProcessing(message, null);
    firstParent.end();
    secondParent.end();

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertProcessDuration(metrics, 2);
    assertThat(spanExporter.getFinishedSpanItems())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.spring-pulsar-1.0"))
        .extracting(span -> span.getParentSpanId())
        .containsExactlyInAnyOrder(
            firstParent.getSpanContext().getSpanId(), secondParent.getSpanContext().getSpanId());
  }

  @Test
  void nestedProcessingKeepsIndependentParentsAndRestoresScopes() {
    assumeTrue(emitStableMessagingSemconv());
    Context previous = Context.current();
    Message<?> outerMessage = message("outer-topic");
    Message<?> innerMessage = message("inner-topic");
    Span outerParent = openTelemetry.getTracer("test").spanBuilder("outer-parent").startSpan();
    Span innerParent = openTelemetry.getTracer("test").spanBuilder("inner-parent").startSpan();
    VirtualFieldStore.setProcessParentContext(outerMessage, Context.root().with(outerParent));
    VirtualFieldStore.setProcessParentContext(innerMessage, Context.root().with(innerParent));

    DispatchMessageToListenerAdvice.AdviceScope outer =
        DispatchMessageToListenerAdvice.onEnter(outerMessage);
    Context outerContext = Context.current();
    DispatchMessageToListenerAdvice.AdviceScope inner =
        DispatchMessageToListenerAdvice.onEnter(innerMessage);
    assertThat(outer).isNotNull();
    assertThat(inner).isNotNull();

    DispatchMessageToListenerAdvice.onExit(null, inner);
    assertThat(Context.current()).isSameAs(outerContext);
    DispatchMessageToListenerAdvice.onExit(null, outer);
    assertThat(Context.current()).isSameAs(previous);
    outerParent.end();
    innerParent.end();

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertProcessDuration(metrics, 2);
    assertThat(spanExporter.getFinishedSpanItems())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.spring-pulsar-1.0"))
        .extracting(span -> span.getParentSpanId())
        .containsExactlyInAnyOrder(
            outerParent.getSpanContext().getSpanId(), innerParent.getSpanContext().getSpanId());
  }

  @Test
  void processesWithoutReceiveInstrumentation() {
    Message<?> message = message("test-topic");
    Span parent = openTelemetry.getTracer("test").spanBuilder("parent").startSpan();
    try (Scope ignored = parent.makeCurrent()) {
      runProcessing(message, null);
    } finally {
      parent.end();
    }

    assertThat(spanExporter.getFinishedSpanItems())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.spring-pulsar-1.0"))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getParentSpanId())
                  .isEqualTo(
                      emitStableMessagingSemconv()
                          ? parent.getSpanContext().getSpanId()
                          : PRODUCER_CONTEXT.getSpanId());
              if (emitStableMessagingSemconv()) {
                assertThat(span.getLinks())
                    .singleElement()
                    .satisfies(
                        link -> assertThat(link.getSpanContext()).isEqualTo(PRODUCER_CONTEXT));
              }
            });
  }

  private static Message<?> message(String topic) {
    Message<?> message = mock(Message.class);
    when(message.getTopicName()).thenReturn(topic);
    when(message.getProperties())
        .thenReturn(
            singletonMap(
                "traceparent",
                "00-"
                    + PRODUCER_CONTEXT.getTraceId()
                    + "-"
                    + PRODUCER_CONTEXT.getSpanId()
                    + "-01"));
    return message;
  }

  private static void runProcessing(Message<?> message, Throwable throwable) {
    Context previous = Context.current();
    DispatchMessageToListenerAdvice.AdviceScope adviceScope =
        DispatchMessageToListenerAdvice.onEnter(message);
    assertThat(adviceScope).isNotNull();
    DispatchMessageToListenerAdvice.onExit(throwable, adviceScope);
    assertThat(Context.current()).isSameAs(previous);
  }

  private static void assertProcessDuration(Collection<MetricData> metrics, long expected) {
    assertThat(metrics)
        .filteredOn(metric -> metric.getName().equals("messaging.process.duration"))
        .singleElement()
        .satisfies(
            metric -> {
              assertThat(
                      metric.getHistogramData().getPoints().stream()
                          .mapToLong(point -> point.getCount())
                          .sum())
                  .isEqualTo(expected);
            });
  }
}
