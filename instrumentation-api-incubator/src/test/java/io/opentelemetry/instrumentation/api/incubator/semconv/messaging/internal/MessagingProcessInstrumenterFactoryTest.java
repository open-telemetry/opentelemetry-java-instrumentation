/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingProcessInstrumenterFactoryTest {

  private static final SpanContext ambientParent =
      spanContext("11111111111111111111111111111111", "1111111111111111");
  private static final SpanContext producer =
      spanContext("22222222222222222222222222222222", "2222222222222222");

  private static final TextMapGetter<Map<String, String>> getter =
      new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Test
  void stableUsesProducerAsParentWithoutLink() {
    assumeTrue(emitStableMessagingSemconv());
    Instrumenter<Map<String, String>, Void> instrumenter =
        MessagingProcessInstrumenterFactory.create(
            Instrumenter.<Map<String, String>, Void>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "process"),
            W3CTraceContextPropagator.getInstance(),
            getter,
            false);

    Map<String, String> carrier =
        singletonMap("traceparent", "00-22222222222222222222222222222222-2222222222222222-01");
    Context context = instrumenter.start(Context.root(), carrier);
    instrumenter.end(context, carrier, null, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasTraceId(producer.getTraceId())
                            .hasParentSpanId(producer.getSpanId())
                            .hasLinks()));
  }

  @Test
  void stableDoesNotLinkAmbientParent() {
    assumeTrue(emitStableMessagingSemconv());
    SpanContext localProducer =
        SpanContext.create(
            producer.getTraceId(),
            producer.getSpanId(),
            producer.getTraceFlags(),
            producer.getTraceState());
    Instrumenter<Map<String, String>, Void> instrumenter =
        MessagingProcessInstrumenterFactory.create(
            Instrumenter.<Map<String, String>, Void>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "process"),
            W3CTraceContextPropagator.getInstance(),
            getter,
            false);

    Map<String, String> carrier =
        singletonMap("traceparent", "00-22222222222222222222222222222222-2222222222222222-01");
    Context context = instrumenter.start(Context.root().with(Span.wrap(localProducer)), carrier);
    instrumenter.end(context, carrier, null, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasTraceId(producer.getTraceId())
                            .hasParentSpanId(producer.getSpanId())
                            .hasLinks()));
  }

  @ParameterizedTest
  @MethodSource("receiveInstrumentationSettings")
  void usesExpectedParentAndLink(boolean receiveInstrumentationEnabled, boolean producerIsParent) {
    Instrumenter<Map<String, String>, Void> instrumenter =
        MessagingProcessInstrumenterFactory.create(
            Instrumenter.<Map<String, String>, Void>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "process"),
            W3CTraceContextPropagator.getInstance(),
            getter,
            receiveInstrumentationEnabled);

    Map<String, String> carrier =
        singletonMap("traceparent", "00-22222222222222222222222222222222-2222222222222222-01");
    Context context = instrumenter.start(Context.root().with(Span.wrap(ambientParent)), carrier);
    instrumenter.end(context, carrier, null, null);

    SpanContext expectedParent = producerIsParent ? producer : ambientParent;
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName("process")
                          .hasKind(SpanKind.CONSUMER)
                          .hasTraceId(expectedParent.getTraceId())
                          .hasParentSpanId(expectedParent.getSpanId());
                      if (producerIsParent) {
                        span.hasLinks();
                      } else {
                        span.hasLinks(LinkData.create(producer));
                      }
                    }));
  }

  private static Stream<Arguments> receiveInstrumentationSettings() {
    boolean stable = emitStableMessagingSemconv();
    String semconv = stable ? "stable" : "old";
    return Stream.of(
        argumentSet(semconv + " receive disabled", false, !stable),
        argumentSet(semconv + " receive enabled", true, false));
  }

  private static SpanContext spanContext(String traceId, String spanId) {
    return SpanContext.createFromRemoteParent(
        traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
  }
}
