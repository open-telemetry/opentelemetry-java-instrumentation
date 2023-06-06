/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class HttpClientAttributesExtractorStableSemconvTest {

  static class TestHttpClientAttributesGetter
      implements HttpClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String getUrlFull(Map<String, String> request) {
      return request.get("url");
    }

    @Override
    public String getHttpRequestMethod(Map<String, String> request) {
      return request.get("method");
    }

    @Override
    public List<String> getHttpRequestHeader(Map<String, String> request, String name) {
      String value = request.get("header." + name);
      return value == null ? emptyList() : asList(value.split(","));
    }

    @Override
    public Integer getHttpResponseStatusCode(
        Map<String, String> request, Map<String, String> response, @Nullable Throwable error) {
      String value = response.get("statusCode");
      return value == null ? null : Integer.parseInt(value);
    }

    @Override
    public List<String> getHttpResponseHeader(
        Map<String, String> request, Map<String, String> response, String name) {
      String value = response.get("header." + name);
      return value == null ? emptyList() : asList(value.split(","));
    }
  }

  static class TestNetClientAttributesGetter
      implements NetClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Nullable
    @Override
    public String getNetworkTransport(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("transport");
    }

    @Nullable
    @Override
    public String getNetworkType(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("type");
    }

    @Nullable
    @Override
    public String getNetworkProtocolName(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("protocolName");
    }

    @Nullable
    @Override
    public String getNetworkProtocolVersion(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("protocolVersion");
    }

    @Nullable
    @Override
    public String getServerAddress(Map<String, String> request) {
      return request.get("peerName");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, String> request) {
      String value = request.get("peerPort");
      return value == null ? null : Integer.parseInt(value);
    }
  }

  @Test
  void normal() {
    Map<String, String> request = new HashMap<>();
    request.put("method", "POST");
    request.put("url", "http://github.com");
    request.put("header.content-length", "10");
    request.put("header.user-agent", "okhttp 3.x");
    request.put("header.custom-request-header", "123,456");
    request.put("transport", "udp");
    request.put("type", "ipv4");
    request.put("protocolName", "http");
    request.put("protocolVersion", "1.1");
    request.put("peerName", "github.com");
    request.put("peerPort", "123");

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("header.content-length", "20");
    response.put("header.custom-response-header", "654,321");

    ToIntFunction<Context> resendCountFromContext = context -> 2;

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        new HttpClientAttributesExtractor<>(
            new TestHttpClientAttributesGetter(),
            new TestNetClientAttributesGetter(),
            singletonList("Custom-Request-Header"),
            singletonList("Custom-Response-Header"),
            resendCountFromContext);

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
            entry(UrlAttributes.URL_FULL, "http://github.com"),
            entry(SemanticAttributes.USER_AGENT_ORIGINAL, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")),
            entry(NetworkAttributes.SERVER_ADDRESS, "github.com"),
            entry(NetworkAttributes.SERVER_PORT, 123L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, response, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(HttpAttributes.HTTP_REQUEST_BODY_SIZE, 10L),
            entry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 202L),
            entry(HttpAttributes.HTTP_RESPONSE_BODY_SIZE, 20L),
            entry(SemanticAttributes.HTTP_RESEND_COUNT, 2L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")),
            entry(NetworkAttributes.NETWORK_TRANSPORT, "udp"),
            entry(NetworkAttributes.NETWORK_TYPE, "ipv4"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"));
  }

  @ParameterizedTest
  @ArgumentsSource(NetworkTransportAndProtocolProvider.class)
  void skipNetworkTransportIfDefaultForProtocol(
      String observedProtocolName,
      String observedProtocolVersion,
      String observedTransport,
      @Nullable String extractedTransport) {
    Map<String, String> request = new HashMap<>();
    request.put("protocolName", observedProtocolName);
    request.put("protocolVersion", observedProtocolVersion);
    request.put("transport", observedTransport);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(
            new TestHttpClientAttributesGetter(), new TestNetClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    if (extractedTransport != null) {
      assertThat(attributes.build())
          .containsEntry(NetworkAttributes.NETWORK_TRANSPORT, extractedTransport);
    } else {
      assertThat(attributes.build()).doesNotContainKey(NetworkAttributes.NETWORK_TRANSPORT);
    }
  }

  static final class NetworkTransportAndProtocolProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          arguments("http", "1.0", "tcp", null),
          arguments("http", "1.1", "tcp", null),
          arguments("http", "2.0", "tcp", null),
          arguments("http", "3.0", "udp", null),
          arguments("http", "1.1", "udp", "udp"),
          arguments("ftp", "2.0", "tcp", "tcp"),
          arguments("http", "3.0", "tcp", "tcp"),
          arguments("http", "42", "tcp", "tcp"));
    }
  }
}
