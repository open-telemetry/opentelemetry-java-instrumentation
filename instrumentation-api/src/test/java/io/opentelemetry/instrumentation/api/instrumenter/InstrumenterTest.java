/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

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
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
                  entry("linkSpanId", LINK_SPAN_ID),
                  entry("Forwarded", "for=1.1.1.1"))
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
        AttributesBuilder attributes,
        Map<String, String> request,
        Map<String, String> response,
        @Nullable Throwable error) {
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
        AttributesBuilder attributes,
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

  @Mock HttpAttributesExtractor<Map<String, String>, Map<String, String>> mockHttpAttributes;
  @Mock DbAttributesExtractor<Map<String, String>, Map<String, String>> mockDbAttributes;

  @Mock
  MessagingAttributesExtractor<Map<String, String>, Map<String, String>> mockMessagingAttributes;

  @Mock RpcAttributesExtractor<Map<String, String>, Map<String, String>> mockRpcAttributes;
  @Mock NetAttributesExtractor<Map<String, String>, Map<String, String>> mockNetAttributes;

  @Test
  void server() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
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
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
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
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
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
  void server_http() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(
                mockHttpAttributes,
                mockNetAttributes,
                new AttributesExtractor1(),
                new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
            .newServerInstrumenter(new MapGetter());

    when(mockNetAttributes.peerIp(REQUEST, null)).thenReturn("2.2.2.2");
    when(mockNetAttributes.peerIp(REQUEST, RESPONSE)).thenReturn("2.2.2.2");

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
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes)
                                        .containsEntry(SemanticAttributes.NET_PEER_IP, "2.2.2.2")
                                        .containsEntry(
                                            SemanticAttributes.HTTP_CLIENT_IP, "1.1.1.1"))));
  }

  @Test
  void server_http_xForwardedFor() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(
                mockHttpAttributes,
                mockNetAttributes,
                new AttributesExtractor1(),
                new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
            .newServerInstrumenter(new MapGetter());

    Map<String, String> request = new HashMap<>(REQUEST);
    request.remove("Forwarded");
    request.put("X-Forwarded-For", "1.1.1.1");

    when(mockNetAttributes.peerIp(request, null)).thenReturn("2.2.2.2");
    when(mockNetAttributes.peerIp(request, RESPONSE)).thenReturn("2.2.2.2");

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
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes)
                                        .containsEntry(SemanticAttributes.NET_PEER_IP, "2.2.2.2")
                                        .containsEntry(
                                            SemanticAttributes.HTTP_CLIENT_IP, "1.1.1.1"))));
  }

  @Test
  void server_http_noForwarded() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
                otelTesting.getOpenTelemetry(), "test", unused -> "span")
            .addAttributesExtractors(
                mockHttpAttributes,
                mockNetAttributes,
                new AttributesExtractor1(),
                new AttributesExtractor2())
            .addSpanLinksExtractor(new LinksExtractor())
            .newServerInstrumenter(new MapGetter());

    Map<String, String> request = new HashMap<>(REQUEST);
    request.remove("Forwarded");

    when(mockNetAttributes.peerIp(request, null)).thenReturn("2.2.2.2");
    when(mockNetAttributes.peerIp(request, RESPONSE)).thenReturn("2.2.2.2");

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
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes)
                                        .containsEntry(SemanticAttributes.NET_PEER_IP, "2.2.2.2")
                                        .containsEntry(
                                            SemanticAttributes.HTTP_CLIENT_IP, "2.2.2.2"))));
  }

  @Test
  void client() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
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

  @Test
  void shouldStartSpanWithGivenStartTime() {
    // given
    Instrumenter<Instant, Instant> instrumenter =
        Instrumenter.<Instant, Instant>newBuilder(
                otelTesting.getOpenTelemetry(), "test", request -> "test span")
            .setTimeExtractors(request -> request, (request, response, error) -> response)
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
        Instrumenter.<String, String>newBuilder(
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
  void extractForwarded() {
    assertThat(ServerInstrumenter.extractForwarded("for=1.1.1.1")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedIpv6() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedWithPort() {
    assertThat(ServerInstrumenter.extractForwarded("for=\"1.1.1.1:2222\"")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedIpv6WithPort() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedCaps() {
    assertThat(ServerInstrumenter.extractForwarded("For=1.1.1.1")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMalformed() {
    assertThat(ServerInstrumenter.extractForwarded("for=;for=1.1.1.1")).isNull();
  }

  @Test
  void extractForwardedEmpty() {
    assertThat(ServerInstrumenter.extractForwarded("")).isNull();
  }

  @Test
  void extractForwardedEmptyValue() {
    assertThat(ServerInstrumenter.extractForwarded("for=")).isNull();
  }

  @Test
  void extractForwardedEmptyValueWithSemicolon() {
    assertThat(ServerInstrumenter.extractForwarded("for=;")).isNull();
  }

  @Test
  void extractForwardedNoFor() {
    assertThat(ServerInstrumenter.extractForwarded("by=1.1.1.1;test=1.1.1.1")).isNull();
  }

  @Test
  void extractForwardedMultiple() {
    assertThat(ServerInstrumenter.extractForwarded("for=1.1.1.1;for=1.2.3.4")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMultipleIpV6() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedMultipleWithPort() {
    assertThat(ServerInstrumenter.extractForwarded("for=\"1.1.1.1:2222\";for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMultipleIpV6WithPort() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedMixedSplitter() {
    assertThat(
            ServerInstrumenter.extractForwarded("test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMixedSplitterIpv6() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedMixedSplitterWithPort() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "test=abcd; by=1.2.3.4, for=\"1.1.1.1:2222\";for=1.2.3.4"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedMixedSplitterIpv6WithPort() {
    assertThat(
            ServerInstrumenter.extractForwarded(
                "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedFor() {
    assertThat(ServerInstrumenter.extractForwardedFor("1.1.1.1")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForIpv6() {
    assertThat(
            ServerInstrumenter.extractForwardedFor("\"[1111:1111:1111:1111:1111:1111:1111:1111]\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForIpv6Unquoted() {
    assertThat(ServerInstrumenter.extractForwardedFor("[1111:1111:1111:1111:1111:1111:1111:1111]"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForIpv6Unbracketed() {
    assertThat(ServerInstrumenter.extractForwardedFor("1111:1111:1111:1111:1111:1111:1111:1111"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForWithPort() {
    assertThat(ServerInstrumenter.extractForwardedFor("1.1.1.1:2222")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForIpv6WithPort() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForIpv6UnquotedWithPort() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "[1111:1111:1111:1111:1111:1111:1111:1111]:2222"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForEmpty() {
    assertThat(ServerInstrumenter.extractForwardedFor("")).isNull();
  }

  @Test
  void extractForwardedForMultiple() {
    assertThat(ServerInstrumenter.extractForwardedFor("1.1.1.1,1.2.3.4")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForMultipleIpv6() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]\",1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleIpv6Unquoted() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "[1111:1111:1111:1111:1111:1111:1111:1111],1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleIpv6Unbracketed() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "1111:1111:1111:1111:1111:1111:1111:1111,1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleWithPort() {
    assertThat(ServerInstrumenter.extractForwardedFor("1.1.1.1:2222,1.2.3.4")).isEqualTo("1.1.1.1");
  }

  @Test
  void extractForwardedForMultipleIpv6WithPort() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\",1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
  }

  @Test
  void extractForwardedForMultipleIpv6UnquotedWithPort() {
    assertThat(
            ServerInstrumenter.extractForwardedFor(
                "[1111:1111:1111:1111:1111:1111:1111:1111]:2222,1.2.3.4"))
        .isEqualTo("1111:1111:1111:1111:1111:1111:1111:1111");
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
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(false, mockDbAttributes);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(false, mockHttpAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isFalse();
  }

  @Test
  void clientNestedSuppressed_whenSameInstrumentationType() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(true, mockDbAttributes);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(true, mockDbAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    assertThat(instrumenterOuter.shouldStart(Context.root(), request)).isTrue();
    assertThat(instrumenterInner.shouldStart(Context.root(), request)).isTrue();

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isFalse();
  }

  @Test
  void clientNestedNotSuppressed_wehnDifferentInstrumentationCategories() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterOuter =
        getInstrumenterWithType(true, mockDbAttributes);
    Instrumenter<Map<String, String>, Map<String, String>> instrumenterInner =
        getInstrumenterWithType(true, mockHttpAttributes);

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
        getInstrumenterWithType(true, new AttributesExtractor[] {null});

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenterOuter.start(Context.root(), request);
    assertThat(instrumenterInner.shouldStart(context, request)).isTrue();
  }

  @Test
  void instrumentationTypeDetected_http() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockHttpAttributes, new AttributesExtractor1());

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.HTTP_CLIENT, context);
  }

  @Test
  void instrumentationTypeDetected_db() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockDbAttributes, new AttributesExtractor2());

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.DB_CLIENT, context);
  }

  @Test
  void instrumentationTypeDetected_rpc() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockRpcAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.RPC_CLIENT, context);
  }

  @Test
  void instrumentationTypeDetected_messaging() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, mockMessagingAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.MESSAGING_PRODUCER, context);
  }

  @Test
  void instrumentationTypeDetected_mix() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(
            true,
            new AttributesExtractor2(),
            mockMessagingAttributes,
            mockNetAttributes,
            mockDbAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    validateInstrumentationTypeSpanPresent(SpanKey.MESSAGING_PRODUCER, context);
  }

  @Test
  void instrumentationTypeDetected_generic() {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        getInstrumenterWithType(true, new AttributesExtractor2(), mockNetAttributes);

    Map<String, String> request = new HashMap<>(REQUEST);

    Context context = instrumenter.start(Context.root(), request);
    Span span = Span.fromContext(context);

    assertThat(span).isNotNull();

    assertThat(SpanKey.HTTP_CLIENT.fromContextOrNull(context)).isNull();
    assertThat(SpanKey.DB_CLIENT.fromContextOrNull(context)).isNull();
    assertThat(SpanKey.RPC_CLIENT.fromContextOrNull(context)).isNull();
    assertThat(SpanKey.MESSAGING_PRODUCER.fromContextOrNull(context)).isNull();
  }

  private static void validateInstrumentationTypeSpanPresent(SpanKey spanKey, Context context) {
    Span span = Span.fromContext(context);

    assertThat(span).isNotNull();
    assertThat(spanKey.fromContextOrNull(context)).isSameAs(span);
  }

  private static Instrumenter<Map<String, String>, Map<String, String>> getInstrumenterWithType(
      boolean enableInstrumentation, AttributesExtractor... attributeExtractors) {
    InstrumenterBuilder<Map<String, String>, Map<String, String>> builder =
        Instrumenter.<Map<String, String>, Map<String, String>>newBuilder(
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
