/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
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
import io.opentelemetry.instrumentation.api.internal.SchemaUrlProvider;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
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

  static class AttributesExtractorWithSchemaUrl
      implements AttributesExtractor<Map<String, String>, Map<String, String>>, SchemaUrlProvider {

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, Map<String, String> request) {
      attributes.put("key", "value");
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Map<String, String> request,
        @Nullable Map<String, String> response,
        @Nullable Throwable error) {}

    @Nullable
    @Override
    public String internalGetSchemaUrl() {
      return "schemaUrl from extractor";
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

  @Mock AttributesExtractor<Map<String, String>, Map<String, String>> mockNetClientAttributes;

  @Test
  void server() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractor(new AttributesExtractor1())
            .addAttributesExtractor(new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
            .buildServerInstrumenter(new MapGetter());

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
                            .hasKind(SpanKind.SERVER)
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasLinks(expectedSpanLink())
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("req1"), "req1_value"),
                                equalTo(AttributeKey.stringKey("req2"), "req2_2_value"),
                                equalTo(AttributeKey.stringKey("req3"), "req3_value"),
                                equalTo(AttributeKey.stringKey("resp1"), "resp1_value"),
                                equalTo(AttributeKey.stringKey("resp2"), "resp2_2_value"),
                                equalTo(AttributeKey.stringKey("resp3"), "resp3_value"))));
  }

  @Test
  void server_error() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractor(new AttributesExtractor1())
            .addAttributesExtractor(new AttributesExtractor2())
            .buildServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    assertThat(Span.fromContext(context).getSpanContext().isValid()).isTrue();

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
            .addAttributesExtractor(new AttributesExtractor1())
            .addAttributesExtractor(new AttributesExtractor2())
            .buildServerInstrumenter(new MapGetter());

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
    assertThat(Span.fromContext(context).getSpanContext().isValid()).isTrue();
    assertThat(LocalRootSpan.fromContext(context)).isSameAs(Span.fromContext(context));

    instrumenter.end(context, request, RESPONSE, null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                            .hasSpanId(Span.fromContext(context).getSpanContext().getSpanId())
                            .hasParentSpanId("090a0b0c0d0e0f00")));
  }

  @Test
  void client() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractor(new AttributesExtractor1())
            .addAttributesExtractor(new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
            .buildClientInstrumenter(Map::put);

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
                            .hasInstrumentationScopeInfo(InstrumentationScopeInfo.create("test"))
                            .hasTraceId(spanContext.getTraceId())
                            .hasSpanId(spanContext.getSpanId())
                            .hasParentSpanId(SpanId.getInvalid())
                            .hasStatus(StatusData.unset())
                            .hasLinks(expectedSpanLink())
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("req1"), "req1_value"),
                                equalTo(AttributeKey.stringKey("req2"), "req2_2_value"),
                                equalTo(AttributeKey.stringKey("req3"), "req3_value"),
                                equalTo(AttributeKey.stringKey("resp1"), "resp1_value"),
                                equalTo(AttributeKey.stringKey("resp2"), "resp2_2_value"),
                                equalTo(AttributeKey.stringKey("resp3"), "resp3_value"))));
  }

  @Test
  void client_error() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractor(new AttributesExtractor1())
            .addAttributesExtractor(new AttributesExtractor2())
            .buildClientInstrumenter(Map::put);

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
            .addAttributesExtractor(new AttributesExtractor1())
            .addAttributesExtractor(new AttributesExtractor2())
            .buildClientInstrumenter(Map::put);

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
    assertThat(LocalRootSpan.fromContextOrNull(context)).isNull();

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
  void upstream_customSpanKind() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildUpstreamInstrumenter(new MapGetter(), SpanKindExtractor.alwaysInternal());

    Map<String, String> request = new HashMap<>();
    request.put("traceparent", "00-ff01020304050600ff0a0b0c0d0e0f00-090a0b0c0d0e0f00-01");
    Context context = instrumenter.start(Context.root(), request);

    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();

    instrumenter.end(context, request, emptyMap(), null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasKind(SpanKind.INTERNAL)
                            .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                            .hasParentSpanId("090a0b0c0d0e0f00")));
  }

  @Test
  void downstream_customSpanKind() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .buildDownstreamInstrumenter(Map::put, SpanKindExtractor.alwaysInternal());

    Map<String, String> request = new HashMap<>();
    Context context = instrumenter.start(Context.root(), request);

    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    assertThat(spanContext.isValid()).isTrue();
    assertThat(request).containsKey("traceparent");

    instrumenter.end(context, request, emptyMap(), null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void operationListeners() {
    AtomicReference<Boolean> startContext = new AtomicReference<>();
    AtomicReference<Boolean> endContext = new AtomicReference<>();

    OperationListener operationListener =
        new OperationListener() {
          @Override
          public Context onStart(Context context, Attributes startAttributes, long startNanos) {
            startContext.set(true);
            return context;
          }

          @Override
          public void onEnd(Context context, Attributes endAttributes, long endNanos) {
            endContext.set(true);
          }
        };

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addOperationListener(operationListener)
            .buildServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    instrumenter.end(context, REQUEST, RESPONSE, null);

    assertThat(startContext.get()).isTrue();
    assertThat(endContext.get()).isTrue();
  }

  @Test
  void operationMetrics() {
    AtomicReference<Context> startContext = new AtomicReference<>();
    AtomicReference<Context> endContext = new AtomicReference<>();

    OperationListener operationListener =
        new OperationListener() {
          @Override
          public Context onStart(Context context, Attributes startAttributes, long startNanos) {
            startContext.set(context);
            return context;
          }

          @Override
          public void onEnd(Context context, Attributes endAttributes, long endNanos) {
            endContext.set(context);
          }
        };

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addOperationMetrics(meter -> operationListener)
            .buildServerInstrumenter(new MapGetter());

    Context context = instrumenter.start(Context.root(), REQUEST);
    instrumenter.end(context, REQUEST, RESPONSE, null);

    assertThat(Span.fromContext(startContext.get()).getSpanContext().isValid()).isTrue();
    assertThat(Span.fromContext(endContext.get()).getSpanContext().isValid()).isTrue();
  }

  @Test
  void shouldNotAddInvalidLink() {
    // given
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "test span")
            .addSpanLinksExtractor(
                (spanLinks, parentContext, request) -> spanLinks.addLink(SpanContext.getInvalid()))
            .buildInstrumenter();

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
            .buildInstrumenter();

    // when
    Context context = instrumenter.start(Context.root(), "request");

    // then
    assertThat(context.get(testKey)).isEqualTo("testVal");
  }

  @Test
  void shouldDisableInstrumenter() {
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "test span")
            .setEnabled(false)
            .buildInstrumenter();

    assertThat(instrumenter.shouldStart(Context.root(), "request")).isFalse();
  }

  @Test
  void instrumentationVersion_default() {
    InstrumenterBuilder<Map<String, String>, Map<String, String>> builder =
        Instrumenter.builder(
            otelTesting.getOpenTelemetry(), "test-instrumentation", name -> "span");

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        builder.buildInstrumenter();

    Context context = instrumenter.start(Context.root(), emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, emptyMap(), emptyMap(), null);

    // see the test-instrumentation.properties file
    InstrumentationScopeInfo expectedLibraryInfo =
        InstrumentationScopeInfo.builder("test-instrumentation").setVersion("1.2.3").build();

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasInstrumentationScopeInfo(expectedLibraryInfo)));
  }

  @Test
  void instrumentationVersion_custom() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", name -> "span")
            .setInstrumentationVersion("1.0")
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, emptyMap(), emptyMap(), null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("span")
                            .hasInstrumentationScopeInfo(
                                InstrumentationScopeInfo.builder("test")
                                    .setVersion("1.0")
                                    .build())));
  }

  @Test
  void schemaUrl_setExplicitly() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", name -> "span")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.0.0")
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, emptyMap(), emptyMap(), null);

    InstrumentationScopeInfo expectedLibraryInfo =
        InstrumentationScopeInfo.builder("test")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.0.0")
            .build();
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasInstrumentationScopeInfo(expectedLibraryInfo)));
  }

  @Test
  void schemaUrl_computedFromExtractors() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", name -> "span")
            .addAttributesExtractor(new AttributesExtractorWithSchemaUrl())
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, emptyMap(), emptyMap(), null);

    InstrumentationScopeInfo expectedLibraryInfo =
        InstrumentationScopeInfo.builder("test").setSchemaUrl("schemaUrl from extractor").build();
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasInstrumentationScopeInfo(expectedLibraryInfo)));
  }

  @Test
  void schemaUrl_schemaSetExplicitlyOverridesSchemaComputedFromExtractors() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", name -> "span")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.0.0")
            .addAttributesExtractor(new AttributesExtractorWithSchemaUrl())
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), emptyMap());
    assertThat(Span.fromContext(context)).isNotNull();

    instrumenter.end(context, emptyMap(), emptyMap(), null);

    InstrumentationScopeInfo expectedLibraryInfo =
        InstrumentationScopeInfo.builder("test")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.0.0")
            .build();
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("span").hasInstrumentationScopeInfo(expectedLibraryInfo)));
  }

  @Test
  void shouldRetrieveSpanKeysFromAttributesExtractors() {
    when(((SpanKeyProvider) mockDbClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.DB_CLIENT);
    when(((SpanKeyProvider) mockHttpClientAttributes).internalGetSpanKey())
        .thenReturn(SpanKey.HTTP_CLIENT);

    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractor(new AttributesExtractor2())
            .addAttributesExtractor(mockHttpClientAttributes)
            .addAttributesExtractor(mockNetClientAttributes)
            .addAttributesExtractor(mockDbClientAttributes)
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), REQUEST);

    assertThatSpanKeyWasStored(SpanKey.DB_CLIENT, context);
    assertThatSpanKeyWasStored(SpanKey.HTTP_CLIENT, context);
  }

  private static void assertThatSpanKeyWasStored(SpanKey spanKey, Context context) {
    Span span = Span.fromContext(context);
    assertThat(span).isNotNull();
    assertThat(spanKey.fromContextOrNull(context)).isSameAs(span);
  }

  private static LinkData expectedSpanLink() {
    return LinkData.create(
        SpanContext.create(
            LINK_TRACE_ID, LINK_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
  }
}
