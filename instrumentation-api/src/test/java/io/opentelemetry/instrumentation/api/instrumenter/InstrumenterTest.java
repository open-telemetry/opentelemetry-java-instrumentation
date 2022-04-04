/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumenterTest {
  private static final String LINK_TRACE_ID = TraceId.fromLongs(0, 42);
  private static final String LINK_SPAN_ID = SpanId.fromLong(123);

  private static final Map<String, String> REQUEST =
      Collections.unmodifiableMap(
          Stream.of(
                  entry("req1", "req1_value"),
                  entry("req2", "req2_value"),
                  entry("req2_2", "req2_2_value"),
                  entry("req3", "req3_value"),
                  entry("linkTraceId", LINK_TRACE_ID),
                  entry("linkSpanId", LINK_SPAN_ID))
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

  static class LinksExtractor implements SpanLinksExtractor<Map<String, String>> {

    @Override
    public void extract(
        SpanLinksBuilder spanLinks, Context parentContext, Map<String, String> request) {
      spanLinks.addLink(
          SpanContext.create(
              request.get("linkTraceId"),
              request.get("linkSpanId"),
              TraceFlags.getSampled(),
              TraceState.getDefault()));
    }
  }

  static class TestTimeExtractor implements TimeExtractor<Instant, Instant> {

    @Override
    public Instant extractStartTime(Instant request) {
      return request;
    }

    @Override
    public Instant extractEndTime(
        Instant request, @Nullable Instant response, @Nullable Throwable error) {
      return response;
    }
  }

  static class MapGetter implements TextMapGetter<Map<String, String>> {

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

  @Mock(extraInterfaces = SpanKeyProvider.class)
  AttributesExtractor<Map<String, String>, Map<String, String>> mockHttpClientAttributes;

  @Mock(extraInterfaces = SpanKeyProvider.class)
  AttributesExtractor<Map<String, String>, Map<String, String>> mockDbClientAttributes;

  @Mock(extraInterfaces = SpanKeyProvider.class)
  AttributesExtractor<Map<String, String>, Map<String, String>> mockRpcClientAttributes;

  @Mock(extraInterfaces = SpanKeyProvider.class)
  AttributesExtractor<Map<String, String>, Map<String, String>> mockMessagingProducerAttributes;

  @Mock AttributesExtractor<Map<String, String>, Map<String, String>> mockNetClientAttributes;

  @Test
  void server() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
            .newServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(SpanKey.SERVER.fromContextOrNull(context).getSpanContext()).isEqualTo(spanContext);

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
                            .hasLinks(expectedSpanLink())
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
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    SpanContext spanContext = Span.fromContext(context).getSpanContext();

    assertThat(spanContext.isValid()).isTrue();
    assertThat(SpanKey.SERVER.fromContextOrNull(context).getSpanContext()).isEqualTo(spanContext);

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
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .newServerInstrumenter(new MapGetter());

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
    assertThat(SpanKey.SERVER.fromContextOrNull(context).getSpanContext()).isEqualTo(spanContext);

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
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(new AttributesExtractor1(), new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
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
                            .hasLinks(expectedSpanLink())
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
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
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
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
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

  @Test
  void requestListeners() {
    AtomicReference<Boolean> startContext = new AtomicReference<>();
    AtomicReference<Boolean> endContext = new AtomicReference<>();

    RequestListener requestListener =
        new RequestListener() {
          @Override
          public Context start(Context context, Attributes startAttributes, long startNanos) {
            startContext.set(true);
            return context;
          }

          @Override
          public void end(Context context, Attributes endAttributes, long endNanos) {
            endContext.set(true);
          }
        };

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addRequestListener(requestListener)
            .newServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    instrumenter.end(context, REQUEST, RESPONSE, null);

    assertThat(startContext.get()).isTrue();
    assertThat(endContext.get()).isTrue();
  }

  @Test
  void requestMetrics() {
    AtomicReference<Context> startContext = new AtomicReference<>();
    AtomicReference<Context> endContext = new AtomicReference<>();

    RequestListener requestListener =
        new RequestListener() {
          @Override
          public Context start(Context context, Attributes startAttributes, long startNanos) {
            startContext.set(context);
            return context;
          }

          @Override
          public void end(Context context, Attributes endAttributes, long endNanos) {
            endContext.set(context);
          }
        };

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addRequestMetrics(meter -> requestListener)
            .newServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    instrumenter.end(context, REQUEST, RESPONSE, null);

    assertThat(Span.fromContext(startContext.get()).getSpanContext().isValid()).isTrue();
    assertThat(Span.fromContext(endContext.get()).getSpanContext().isValid()).isTrue();
  }

  @Test
  void shouldStartSpanWithGivenStartTime() {
    // given
    Instrumenter<Instant, Instant> instrumenter =
        Instrumenter.<Instant, Instant>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "test span")
            .setTimeExtractor(new TestTimeExtractor())
            .newInstrumenter();

    Instant startTime = Instant.ofEpochSecond(100);
    Instant endTime = Instant.ofEpochSecond(123);

    // when
    Context context = instrumenter.start(Context.root(), startTime);
    instrumenter.end(context, startTime, endTime, null);

    // then
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test span").startsAt(startTime).endsAt(endTime)));
  }

  @Test
  void shouldNotAddInvalidLink() {
    // given
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "test span")
            .addSpanLinksExtractor(
                (spanLinks, parentContext, request) -> spanLinks.addLink(SpanContext.getInvalid()))
            .newInstrumenter();

    // when
    Context context = instrumenter.start(Context.root(), "request");
    instrumenter.end(context, "request", "response", null);

    // then
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test span").hasTotalRecordedLinks(0)));
  }

  @Test
  void shouldUseContextCustomizer() {
    // given
    ContextKey<String> testKey = ContextKey.named("test");
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "test span")
            .addContextCustomizer(
                (context, request, attributes) -> context.with(testKey, "testVal"))
            .newInstrumenter();

    // when
    Context context = instrumenter.start(Context.root(), "request");

    // then
    assertThat(context.get(testKey)).isEqualTo("testVal");
  }

  @Test
  void clientNestedSpansSuppressed_whenInstrumentationTypeDisabled() {
    // this test depends on default config option for InstrumentationType

    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(false);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(false);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isFalse();
  }

  @Test
  void clientNestedSpansSuppressed_whenInstrumentationTypeDisabled2() {
    when(((SpanKeyProvider) mockHttpClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.HTTP_CLIENT);
    when(((SpanKeyProvider) mockDbClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.DB_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(false, mockDbClientAttributes);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(false, mockHttpClientAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isFalse();
  }

  @Test
  void clientNestedSuppressed_whenSameInstrumentationType() {
    when(((SpanKeyProvider) mockDbClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.DB_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(true, mockDbClientAttributes);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(true, mockDbClientAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    assertThat(instrumenterOuter.shouldStart(Context.root(), request)).isTrue();
    assertThat(instrumenterInner.shouldStart(Context.root(), request)).isTrue();

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isFalse();
  }

  @Test
  void clientNestedNotSuppressed_wehnDifferentInstrumentationCategories() {
    when(((SpanKeyProvider) mockHttpClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.HTTP_CLIENT);
    when(((SpanKeyProvider) mockDbClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.DB_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(true, mockDbClientAttributes);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(true, mockHttpClientAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isTrue();
  }

  @Test
  void clientNestedGenericNotSuppressed() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(true, new AttributesExtractor1());
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(true, new AttributesExtractor1());

    Map<String, String> request = new HashMap<>(REQUEST);
    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isTrue();
  }

  @Test
  void clientNestedGenericSpansNotSuppressed_whenNoExtractors() {
    // this test depends on default config option for InstrumentationType

    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(true);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(true, null, null);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isTrue();
  }

  @Test
  void instrumentationTypeDetected_http() {
    when(((SpanKeyProvider) mockHttpClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.HTTP_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockHttpClientAttributes, new AttributesExtractor1());

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.HTTP_CLIENT, context);
  }

  @Test
  void instrumentationTypeDetected_db() {
    when(((SpanKeyProvider) mockDbClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.DB_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockDbClientAttributes, new AttributesExtractor2());

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.DB_CLIENT, context);
  }

  @Test
  void instrumentationTypeDetected_rpc() {
    when(((SpanKeyProvider) mockRpcClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.RPC_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockRpcClientAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.RPC_CLIENT, context);
  }

  @Test
  void instrumentationTypeDetected_producer() {
    when(((SpanKeyProvider) mockMessagingProducerAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.PRODUCER);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockMessagingProducerAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.PRODUCER, context);
  }

  @Test
  void instrumentationTypeDetected_mix() {
    when(((SpanKeyProvider) mockDbClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.DB_CLIENT);
    when(((SpanKeyProvider) mockMessagingProducerAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.PRODUCER);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(
            true,
            new AttributesExtractor2(),
            mockMessagingProducerAttributes,
            mockNetClientAttributes,
            mockDbClientAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.PRODUCER, context);
  }

  @Test
  void instrumentationTypeDetected_generic() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, new AttributesExtractor2(), mockNetClientAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    Span span = Span.fromContext(context);

    assertThat(span).isNotNull();

    assertThat(SpanKey.HTTP_CLIENT.fromContextOrNull(context)).isNull();
    assertThat(SpanKey.DB_CLIENT.fromContextOrNull(context)).isNull();
    assertThat(SpanKey.RPC_CLIENT.fromContextOrNull(context)).isNull();
    assertThat(SpanKey.PRODUCER.fromContextOrNull(context)).isNull();
  }

  @Test
  void instrumentationVersion_default() {
    InstrumenterBuilder<Map<String, String>, Map<String, String>> builder =
        Instrumenter.builder(
            otelTesting.getOpenTelemetry(), "test-instrumentation", name -> "span");

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter = builder.newInstrumenter();

    Context context = instrumenter.start(Context.root(), Collections.emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, Collections.emptyMap(), Collections.emptyMap(), null);

    // see the test-instrumentation.properties file
    InstrumentationLibraryInfo expectedLibraryInfo =
        InstrumentationLibraryInfo.create("test-instrumentation", "1.2.3");

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span").hasInstrumentationLibraryInfo(expectedLibraryInfo)));
  }

  @Test
  void instrumentationVersion_custom() {
    InstrumenterBuilder<Map<String, String>, Map<String, String>> builder =
        Instrumenter.builder(otelTesting.getOpenTelemetry(), "test", "1.0", name -> "span");

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter = builder.newInstrumenter();

    Context context = instrumenter.start(Context.root(), Collections.emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, Collections.emptyMap(), Collections.emptyMap(), null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasInstrumentationLibraryInfo(
                                InstrumentationLibraryInfo.create("test", "1.0"))));
  }

  private static void validateInstrumentationTypeSpanPresent(SpanKey spanKey, Context context) {
    Span span = Span.fromContext(context);

    assertThat(span).isNotNull();
    assertThat(spanKey.fromContextOrNull(context)).isSameAs(span);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static Instrumenter<Map<String, String>, Map<String, String>> getInstrumenterWithType(
      boolean enableInstrumentation,
      AttributesExtractor<Map<String, String>, Map<String, String>>... attributeExtractors) {
    InstrumenterBuilder<Map<String, String>, Map<String, String>> builder =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(attributeExtractors)
            .enableInstrumentationTypeSuppression(enableInstrumentation);

    return builder.newClientInstrumenter(Map::put);
  }

  private static LinkData expectedSpanLink() {
    return LinkData.create(
        SpanContext.create(
            LINK_TRACE_ID, LINK_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
  }
}
