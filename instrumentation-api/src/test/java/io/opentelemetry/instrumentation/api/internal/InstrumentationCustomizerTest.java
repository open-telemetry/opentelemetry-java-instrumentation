/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer.InstrumentationType;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.internal.InternalInstrumenterCustomizerProviderImpl;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentationCustomizerTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

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

  @Mock private OperationListener operationListener;
  @Mock private ContextCustomizer<Object> contextCustomizer;

  private List<InternalInstrumenterCustomizerProvider> originalCustomizerProviders;

  @BeforeEach
  void beforeEach() {
    originalCustomizerProviders =
        InternalInstrumenterCustomizerUtil.getInstrumenterCustomizerProviders();
  }

  @AfterEach
  void afterEach() {
    InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(
        originalCustomizerProviders);
  }

  static void setCustomizer(InstrumenterCustomizerProvider provider) {
    InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(
        singletonList(new InternalInstrumenterCustomizerProviderImpl(provider)));
  }

  @Test
  void testGetInstrumentationName() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          assertThat(customizer.getInstrumentationName()).isEqualTo("test");
        });

    Instrumenter.<Map<String, String>, Map<String, String>>builder(
            otelTesting.getOpenTelemetry(), "test", unused -> "span")
        .buildInstrumenter();

    assertThat(customizerCalled).isTrue();
  }

  @Test
  void testHasType() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          assertThat(customizer.hasType(InstrumentationType.HTTP_CLIENT)).isTrue();
          assertThat(customizer.hasType(InstrumentationType.HTTP_SERVER)).isFalse();
        });

    class TestAttributesExtractor implements AttributesExtractor<Object, Object>, SpanKeyProvider {
      @Override
      public void onStart(AttributesBuilder attributes, Context parentContext, Object request) {}

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          Object request,
          @Nullable Object response,
          @Nullable Throwable error) {}

      @Nullable
      @Override
      public SpanKey internalGetSpanKey() {
        return SpanKey.HTTP_CLIENT;
      }
    }

    Instrumenter.<Map<String, String>, Map<String, String>>builder(
            otelTesting.getOpenTelemetry(), "test", unused -> "span")
        .addAttributesExtractor(new TestAttributesExtractor())
        .buildInstrumenter();

    assertThat(customizerCalled).isTrue();
  }

  @Test
  void testAddAttributesExtractor() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.addAttributesExtractor(new AttributesExtractor1());
        });

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.INTERNAL)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("req1"), "req1_value"),
                                equalTo(AttributeKey.stringKey("req2"), "req2_value"),
                                equalTo(AttributeKey.stringKey("resp1"), "resp1_value"),
                                equalTo(AttributeKey.stringKey("resp2"), "resp2_value"))));
  }

  @Test
  void testAddAttributesExtractors() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.addAttributesExtractors(
              Arrays.asList(new AttributesExtractor1(), new AttributesExtractor2()));
        });

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.INTERNAL)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("req1"), "req1_value"),
                                equalTo(AttributeKey.stringKey("req2"), "req2_2_value"),
                                equalTo(AttributeKey.stringKey("req3"), "req3_value"),
                                equalTo(AttributeKey.stringKey("resp1"), "resp1_value"),
                                equalTo(AttributeKey.stringKey("resp2"), "resp2_2_value"),
                                equalTo(AttributeKey.stringKey("resp3"), "resp3_value"))));
  }

  @Test
  void testAddOperationMetrics() {
    when(operationListener.onStart(any(), any(), anyLong())).thenAnswer(i -> i.getArguments()[0]);

    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.addOperationMetrics(meter -> operationListener);
        });

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.INTERNAL)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributes(Attributes.empty())));

    verify(operationListener).onStart(any(), any(), anyLong());
    verify(operationListener).onEnd(any(), any(), anyLong());
  }

  @Test
  void testAddContextCustomizer() {
    when(contextCustomizer.onStart(any(), any(), any())).thenAnswer(i -> i.getArguments()[0]);

    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.addContextCustomizer(contextCustomizer);
        });

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.INTERNAL)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributes(Attributes.empty())));

    verify(contextCustomizer).onStart(any(), any(), any());
  }

  @Test
  void testSetSpanNameExtractor() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.setSpanNameExtractor(
              unused -> (SpanNameExtractor<Object>) object -> "new name");
        });

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("new name")
                            .hasKind(SpanKind.INTERNAL)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasAttributes(Attributes.empty())));
  }

  @Test
  void testSetSpanStatusExtractor() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.setSpanStatusExtractor(
              unused ->
                  (spanStatusBuilder, request, response, error) ->
                      spanStatusBuilder.setStatus(StatusCode.OK));
        });

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .setSpanStatusExtractor(
                (spanStatusBuilder, request, response, error) ->
                    spanStatusBuilder.setStatus(StatusCode.ERROR))
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, REQUEST, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.INTERNAL)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.ok())
                            .hasAttributes(Attributes.empty())));
  }

  @Test
  void testAddShouldStartFilter() {
    AtomicBoolean customizerCalled = new AtomicBoolean();
    setCustomizer(
        customizer -> {
          customizerCalled.set(true);
          customizer.addShouldStartFilter(
              (context, request, spanKind, instrumentationName) -> !request.equals("blocked"));
        });

    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildInstrumenter();

    assertThat(customizerCalled).isTrue();

    assertThat(instrumenter.shouldStart(Context.root(), "allowed")).isTrue();
    assertThat(instrumenter.shouldStart(Context.root(), "blocked")).isFalse();
  }

  static class AttributesExtractor1
      implements AttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, Map<String, String> request) {
      attributes.put("req1", request.get("req1"));
      attributes.put("req2", request.get("req2"));
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Map<String, String> request,
        Map<String, String> response,
        @Nullable Throwable error) {
      attributes.put("resp1", response.get("resp1"));
      attributes.put("resp2", response.get("resp2"));
    }
  }

  static class AttributesExtractor2
      implements AttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, Map<String, String> request) {
      attributes.put("req3", request.get("req3"));
      attributes.put("req2", request.get("req2_2"));
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Map<String, String> request,
        Map<String, String> response,
        @Nullable Throwable error) {
      attributes.put("resp3", response.get("resp3"));
      attributes.put("resp2", response.get("resp2_2"));
    }
  }
}
