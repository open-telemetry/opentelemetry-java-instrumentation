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
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class HttpServerAttributesExtractorStableSemconvTest {

  static class TestHttpServerAttributesGetter
      implements HttpServerAttributesGetter<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getHttpRequestMethod(Map<String, Object> request) {
      return (String) request.get("method");
    }

    @Override
    public String getUrlScheme(Map<String, Object> request) {
      return (String) request.get("scheme");
    }

    @Nullable
    @Override
    public String getUrlPath(Map<String, Object> request) {
      return (String) request.get("path");
    }

    @Nullable
    @Override
    public String getUrlQuery(Map<String, Object> request) {
      return (String) request.get("query");
    }

    @Override
    public String getHttpRoute(Map<String, Object> request) {
      return (String) request.get("route");
    }

    @Override
    public List<String> getHttpRequestHeader(Map<String, Object> request, String name) {
      String values = (String) request.get("header." + name);
      return values == null ? emptyList() : asList(values.split(","));
    }

    @Override
    public Integer getHttpResponseStatusCode(
        Map<String, Object> request, Map<String, Object> response, @Nullable Throwable error) {
      String value = (String) response.get("statusCode");
      return value == null ? null : Integer.parseInt(value);
    }

    @Override
    public List<String> getHttpResponseHeader(
        Map<String, Object> request, Map<String, Object> response, String name) {
      String values = (String) response.get("header." + name);
      return values == null ? emptyList() : asList(values.split(","));
    }
  }

  static class TestNetServerAttributesGetter
      implements NetServerAttributesGetter<Map<String, Object>, Map<String, Object>> {

    @Nullable
    @Override
    public String getNetworkTransport(
        Map<String, Object> request, @Nullable Map<String, Object> response) {
      return (String) request.get("transport");
    }

    @Nullable
    @Override
    public String getNetworkType(
        Map<String, Object> request, @Nullable Map<String, Object> response) {
      return (String) request.get("type");
    }

    @Nullable
    @Override
    public String getNetworkProtocolName(
        Map<String, Object> request, Map<String, Object> response) {
      return (String) request.get("protocolName");
    }

    @Nullable
    @Override
    public String getNetworkProtocolVersion(
        Map<String, Object> request, Map<String, Object> response) {
      return (String) request.get("protocolVersion");
    }

    @Nullable
    @Override
    public String getServerAddress(Map<String, Object> request) {
      return (String) request.get("hostName");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, Object> request) {
      return (Integer) request.get("hostPort");
    }
  }

  @Test
  void normal() {
    Map<String, Object> request = new HashMap<>();
    request.put("method", "POST");
    request.put("url", "http://github.com");
    request.put("path", "/repositories/1");
    request.put("query", "details=true");
    request.put("scheme", "http");
    request.put("header.content-length", "10");
    request.put("route", "/repositories/{id}");
    request.put("header.user-agent", "okhttp 3.x");
    request.put("header.host", "github.com");
    request.put("header.forwarded", "for=1.1.1.1;proto=https");
    request.put("header.custom-request-header", "123,456");
    request.put("transport", "udp");
    request.put("type", "ipv4");
    request.put("protocolName", "http");
    request.put("protocolVersion", "2.0");

    Map<String, Object> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("header.content-length", "20");
    response.put("header.custom-response-header", "654,321");

    Function<Context, String> routeFromContext = ctx -> "/repositories/{repoId}";

    HttpServerAttributesExtractor<Map<String, Object>, Map<String, Object>> extractor =
        new HttpServerAttributesExtractor<>(
            new TestHttpServerAttributesGetter(),
            new TestNetServerAttributesGetter(),
            singletonList("Custom-Request-Header"),
            singletonList("Custom-Response-Header"),
            routeFromContext);

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(NetworkAttributes.SERVER_ADDRESS, "github.com"),
            entry(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
            entry(UrlAttributes.URL_SCHEME, "http"),
            entry(UrlAttributes.URL_PATH, "/repositories/1"),
            entry(UrlAttributes.URL_QUERY, "details=true"),
            entry(SemanticAttributes.USER_AGENT_ORIGINAL, "okhttp 3.x"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{id}"),
            entry(NetworkAttributes.CLIENT_ADDRESS, "1.1.1.1"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, response, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(NetworkAttributes.NETWORK_TRANSPORT, "udp"),
            entry(NetworkAttributes.NETWORK_TYPE, "ipv4"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{repoId}"),
            entry(HttpAttributes.HTTP_REQUEST_BODY_SIZE, 10L),
            entry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 202L),
            entry(HttpAttributes.HTTP_RESPONSE_BODY_SIZE, 20L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")));
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
            new HttpClientAttributesExtractorStableSemconvTest.TestHttpClientAttributesGetter(),
            new HttpClientAttributesExtractorStableSemconvTest.TestNetClientAttributesGetter());

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
