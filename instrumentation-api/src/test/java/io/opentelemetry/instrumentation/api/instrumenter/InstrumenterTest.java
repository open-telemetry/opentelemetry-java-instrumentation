/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class InstrumenterTest {

  private static final Map<String, String> REQUEST =
      Collections.unmodifiableMap(
          Stream.of(
                  entry("req1", "req1_value"),
                  entry("req2", "req2_value"),
                  entry("req2_2", "req2_2_value"),
                  entry("req3", "req3_value"))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

  private static final Map<String, String> RESPONSE =
      Collections.unmodifiableMap(
          Stream.of(
                  entry("resp1", "resp1_value"),
                  entry("resp2", "resp2_value"),
                  entry("resp2_2", "resp2_2_value"),
                  entry("resp3", "resp3_value"))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

  static class AttributesExtractor1
      extends AttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    protected void onStart(AttributesBuilder attributes, Map<String, String> request) {
      attributes.put("req1", request.get("req1"));
      attributes.put("req2", request.get("req2"));
    }

    @Override
    protected void onEnd(
        AttributesBuilder attributes, Map<String, String> request, Map<String, String> response) {
      attributes.put("resp1", response.get("resp1"));
      attributes.put("resp2", response.get("resp2"));
    }
  }

  static class AttributesExtractor2
      extends AttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    protected void onStart(AttributesBuilder attributes, Map<String, String> request) {
      attributes.put("req3", request.get("req3"));
      attributes.put("req2", request.get("req2_2"));
    }

    @Override
    protected void onEnd(
        AttributesBuilder attributes, Map<String, String> request, Map<String, String> response) {
      attributes.put("resp3", response.get("resp3"));
      attributes.put("resp2", response.get("resp2_2"));
    }
  }

  enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  }

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Test
  void server() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newServerInstrumenter(MapGetter.INSTANCE);

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(ServerSpan.fromContextOrNull(context).getSpanContext()).isEqualTo(spanContext);

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.SERVER)
                            .hasInstrumentationLibraryInfo(
                                InstrumentationLibraryInfo.create("test", null))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes)
                                        .containsOnly(
                                            attributeEntry("req1", "req1_value"),
                                            attributeEntry("req2", "req2_2_value"),
                                            attributeEntry("req3", "req3_value"),
                                            attributeEntry("resp1", "resp1_value"),
                                            attributeEntry("resp2", "resp2_2_value"),
                                            attributeEntry("resp3", "resp3_value")))));
  }

  @Test
  void server_error() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newServerInstrumenter(MapGetter.INSTANCE);

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(ServerSpan.fromContextOrNull(context).getSpanContext()).isEqualTo(spanContext);

    instrumenter.end(context, REQUEST, RESPONSE, new IllegalStateException("test"));

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasStatus(StatusData.error())));
  }

  @Test
  void server_parent() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newServerInstrumenter(MapGetter.INSTANCE);

    Map<String, String> request = new HashMap<>(REQUEST);
    W3CTraceContextPropagator.getInstance()
        .inject(
            Context.root()
                .with(
                    Span.wrap(
                        SpanContext.createFromRemoteParent(
                            "ff01020304050600ff0a0b0c0d0e0f00",
                            "090a0b0c0d0e0f00",
                            TraceFlags.getSampled(),
                            TraceState.getDefault()))),
            request,
            Map::put);
    Context context = instrumenter.start(Context.root(), request);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(ServerSpan.fromContextOrNull(context).getSpanContext()).isEqualTo(spanContext);

    instrumenter.end(context, request, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId("090a0b0c0d0e0f00")));
  }

  @Test
  void client() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newClientInstrumenter(Map::put);

    Map<String, String> request = new HashMap<>(REQUEST);
    Context context = instrumenter.start(Context.root(), request);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(request).containsKey("traceparent");

    instrumenter.end(context, request, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.CLIENT)
                            .hasInstrumentationLibraryInfo(
                                InstrumentationLibraryInfo.create("test", null))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes)
                                        .containsOnly(
                                            attributeEntry("req1", "req1_value"),
                                            attributeEntry("req2", "req2_2_value"),
                                            attributeEntry("req3", "req3_value"),
                                            attributeEntry("resp1", "resp1_value"),
                                            attributeEntry("resp2", "resp2_2_value"),
                                            attributeEntry("resp3", "resp3_value")))));
  }

  @Test
  void client_error() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newClientInstrumenter(Map::put);

    Map<String, String> request = new HashMap<>(REQUEST);
    Context context = instrumenter.start(Context.root(), request);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(request).containsKey("traceparent");

    instrumenter.end(context, request, RESPONSE, new IllegalStateException("test"));

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasStatus(StatusData.error())));
  }

  @Test
  void client_parent() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newClientInstrumenter(Map::put);

    Context parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "ff01020304050600ff0a0b0c0d0e0f00",
                        "090a0b0c0d0e0f00",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));

    Map<String, String> request = new HashMap<>(REQUEST);
    Context context = instrumenter.start(parent, request);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(request).containsKey("traceparent");

    instrumenter.end(context, request, RESPONSE, new IllegalStateException("test"));

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId("090a0b0c0d0e0f00")));
  }
}
