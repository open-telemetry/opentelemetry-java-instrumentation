/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpHeaderCaptureConfigTest {

  @SuppressWarnings("unchecked")
  @ParameterizedTest
  @MethodSource("configProviders")
  void configuresClientAndServerCapture(ConfigProvider configProvider, boolean captureHeaders) {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    try (OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build()) {
      SpringOpenTelemetrySdk openTelemetry = new SpringOpenTelemetrySdk(sdk, configProvider);
      CommonConfig config = new CommonConfig(openTelemetry);
      HttpClientAttributesGetter<String, String> clientGetter =
          mock(HttpClientAttributesGetter.class);
      HttpServerAttributesGetter<String, String> serverGetter =
          mock(HttpServerAttributesGetter.class);
      configureGetter(clientGetter, "client");
      configureGetter(serverGetter, "server");

      record(
          DefaultHttpClientInstrumenterBuilder.create("test-client", openTelemetry, clientGetter)
              .configure(config)
              .build());
      record(
          DefaultHttpServerInstrumenterBuilder.create("test-server", openTelemetry, serverGetter)
              .configure(config)
              .build());

      assertThat(exporter.getFinishedSpanItems()).hasSize(2);
      assertHeaders(exporter.getFinishedSpanItems().get(0), "client", captureHeaders);
      assertHeaders(exporter.getFinishedSpanItems().get(1), "server", captureHeaders);
    }
  }

  private static Stream<Arguments> configProviders() {
    Map<String, String> properties = new HashMap<>();
    for (String role : asList("client", "server")) {
      for (String direction : asList("request", "response")) {
        properties.put(
            "otel.instrumentation.http." + role + "." + direction + "-captured-headers",
            "X-" + role + "-" + direction + ",*,Test-?");
      }
    }
    String yaml =
        "file_format: '1.1'\n"
            + "instrumentation/development:\n"
            + "  general:\n"
            + "    http:\n"
            + "      client:\n"
            + "        request_captured_headers: [X-client-request, '*', Test-?]\n"
            + "        response_captured_headers: [X-client-response, '*', Test-?]\n"
            + "      server:\n"
            + "        request_captured_headers: [X-server-request, '*', Test-?]\n"
            + "        response_captured_headers: [X-server-response, '*', Test-?]\n";
    Map<String, String> emptyProperties = new HashMap<>();
    properties.keySet().forEach(key -> emptyProperties.put(key, ""));
    return Stream.of(
        Arguments.of(flatConfig(properties), true),
        Arguments.of(yamlConfig(yaml), true),
        Arguments.of(flatConfig(emptyMap()), false),
        Arguments.of(flatConfig(emptyProperties), false),
        Arguments.of(yamlConfig("file_format: '1.1'\n"), false),
        Arguments.of(
            yamlConfig(
                yaml.replaceAll("\\[X-(client|server)-(request|response), '\\*', Test-\\?]", "[]")),
            false));
  }

  private static ConfigProvider flatConfig(Map<String, String> properties) {
    return DeclarativeConfigBridge.createInstrumentationConfig(
        DefaultConfigProperties.createFromMap(properties));
  }

  private static ConfigProvider yamlConfig(String yaml) {
    return SdkConfigProvider.create(
        DeclarativeConfiguration.toConfigProperties(
            DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)))));
  }

  private static void configureGetter(
      HttpCommonAttributesGetter<String, String> getter, String role) {
    when(getter.getHttpRequestMethod("request")).thenReturn("GET");
    when(getter.getHttpResponseStatusCode("request", "response", null)).thenReturn(200);
    when(getter.getHttpRequestHeader("request", "x-" + role + "-request"))
        .thenReturn(asList("one", "two"));
    when(getter.getHttpResponseHeader("request", "response", "x-" + role + "-response"))
        .thenReturn(asList("three", "four"));
    when(getter.getHttpRequestHeader("request", "authorization"))
        .thenReturn(asList("sensitive-value"));
    when(getter.getHttpResponseHeader("request", "response", "set-cookie"))
        .thenReturn(asList("sensitive-value"));
  }

  private static void record(Instrumenter<String, String> instrumenter) {
    Context context = instrumenter.start(Context.root(), "request");
    instrumenter.end(context, "request", "response", null);
  }

  private static void assertHeaders(SpanData span, String role, boolean captureHeaders) {
    if (!captureHeaders) {
      assertThat(span.getAttributes().asMap().keySet())
          .noneMatch(key -> key.getKey().startsWith("http.request.header."))
          .noneMatch(key -> key.getKey().startsWith("http.response.header."));
      return;
    }
    assertThat(span.getAttributes().asMap().keySet())
        .filteredOn(
            key ->
                key.getKey().startsWith("http.request.header.")
                    || key.getKey().startsWith("http.response.header."))
        .containsExactlyInAnyOrder(
            stringArrayKey("http.request.header.x-" + role + "-request"),
            stringArrayKey("http.response.header.x-" + role + "-response"));
    assertThat(
            span.getAttributes().get(stringArrayKey("http.request.header.x-" + role + "-request")))
        .containsExactly("one", "two");
    assertThat(
            span.getAttributes()
                .get(stringArrayKey("http.response.header.x-" + role + "-response")))
        .containsExactly("three", "four");
  }
}
