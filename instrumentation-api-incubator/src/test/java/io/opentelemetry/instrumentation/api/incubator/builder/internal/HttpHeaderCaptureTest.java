/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder.internal;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpHeaderCaptureTest {

  @Test
  void retainedExactNameSettersDoNotInterpretWildcards() {
    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

    Map<String, String> request = new LinkedHashMap<>();
    request.put("*", "literal");
    request.put("Authorization", "secret");
    Map<String, String> response = new LinkedHashMap<>();
    response.put("*", "literal");
    response.put("Set-Cookie", "secret");

    try {
      MapGetter getter = new MapGetter();
      Instrumenter<Map<String, String>, Map<String, String>> clientInstrumenter =
          DefaultHttpClientInstrumenterBuilder.create("test", openTelemetry, getter)
              .configure(commonConfig())
              .build();
      Context clientContext = clientInstrumenter.start(Context.root(), request);
      clientInstrumenter.end(clientContext, request, response, null);

      Instrumenter<Map<String, String>, Map<String, String>> serverInstrumenter =
          DefaultHttpServerInstrumenterBuilder.create("test", openTelemetry, getter)
              .configure(commonConfig())
              .build();
      Context serverContext = serverInstrumenter.start(Context.root(), request);
      serverInstrumenter.end(serverContext, request, response, null);

      List<SpanData> spans = spanExporter.getFinishedSpanItems();
      assertThat(spans).hasSize(2);
      assertThat(spans.get(0).getKind()).isEqualTo(SpanKind.CLIENT);
      assertLiteralHeaders(spans.get(0).getAttributes());
      assertThat(spans.get(1).getKind()).isEqualTo(SpanKind.SERVER);
      assertLiteralHeaders(spans.get(1).getAttributes());
    } finally {
      openTelemetry.close();
    }
  }

  private static void assertLiteralHeaders(Attributes attributes) {
    assertThat(attributes.get(stringArrayKey("http.request.header.*")))
        .isEqualTo(singletonList("literal"));
    assertThat(attributes.get(stringArrayKey("http.request.header.authorization"))).isNull();
    assertThat(attributes.get(stringArrayKey("http.response.header.*")))
        .isEqualTo(singletonList("literal"));
    assertThat(attributes.get(stringArrayKey("http.response.header.set-cookie"))).isNull();
  }

  private static CommonConfig commonConfig() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties general =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties common =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getGeneralInstrumentationConfig()).thenReturn(general);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(common);
    when(general
            .get("http")
            .get("client")
            .getScalarList(eq("request_captured_headers"), eq(String.class), anyList()))
        .thenReturn(singletonList("*"));
    when(general
            .get("http")
            .get("client")
            .getScalarList(eq("response_captured_headers"), eq(String.class), anyList()))
        .thenReturn(singletonList("*"));
    when(general
            .get("http")
            .get("server")
            .getScalarList(eq("request_captured_headers"), eq(String.class), anyList()))
        .thenReturn(singletonList("*"));
    when(general
            .get("http")
            .get("server")
            .getScalarList(eq("response_captured_headers"), eq(String.class), anyList()))
        .thenReturn(singletonList("*"));
    when(common.get("http").getScalarList(eq("known_methods"), eq(String.class), anyList()))
        .thenReturn(new ArrayList<>());
    return new CommonConfig(openTelemetry);
  }

  private static final class MapGetter
      implements HttpClientAttributesGetter<Map<String, String>, Map<String, String>>,
          HttpServerAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String getHttpRequestMethod(Map<String, String> request) {
      return "GET";
    }

    @Override
    public List<String> getHttpRequestHeader(Map<String, String> request, String name) {
      return headerValue(request, name);
    }

    @Override
    public Iterable<String> getHttpRequestHeaderNames(Map<String, String> request) {
      return new ArrayList<>(request.keySet());
    }

    @Override
    public Integer getHttpResponseStatusCode(
        Map<String, String> request, Map<String, String> response, Throwable error) {
      return 200;
    }

    @Override
    public List<String> getHttpResponseHeader(
        Map<String, String> request, Map<String, String> response, String name) {
      return headerValue(response, name);
    }

    @Override
    public Iterable<String> getHttpResponseHeaderNames(
        Map<String, String> request, Map<String, String> response) {
      return new ArrayList<>(response.keySet());
    }

    @Override
    public String getUrlFull(Map<String, String> request) {
      return "http://localhost/";
    }

    @Override
    public String getServerAddress(Map<String, String> request) {
      return "localhost";
    }

    @Override
    public Integer getServerPort(Map<String, String> request) {
      return 80;
    }

    @Override
    public String getUrlScheme(Map<String, String> request) {
      return "http";
    }

    @Override
    public String getUrlPath(Map<String, String> request) {
      return "/";
    }

    @Override
    public String getUrlQuery(Map<String, String> request) {
      return null;
    }

    private static List<String> headerValue(Map<String, String> headers, String name) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return singletonList(entry.getValue());
        }
      }
      return new ArrayList<>();
    }
  }
}
