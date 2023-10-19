/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class HttpServerAttributesExtractorBothSemconvTest {

  static class TestHttpServerAttributesGetter
      implements HttpServerAttributesGetter<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getHttpRequestMethod(Map<String, Object> request) {
      return (String) request.get("method");
    }

    @Override
    public String getUrlScheme(Map<String, Object> request) {
      return (String) request.get("urlScheme");
    }

    @Nullable
    @Override
    public String getUrlPath(Map<String, Object> request) {
      return (String) request.get("urlPath");
    }

    @Nullable
    @Override
    public String getUrlQuery(Map<String, Object> request) {
      return (String) request.get("urlQuery");
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

    @Nullable
    @Override
    public String getNetworkTransport(
        Map<String, Object> request, @Nullable Map<String, Object> response) {
      return (String) request.get("networkTransport");
    }

    @Nullable
    @Override
    public String getNetworkType(
        Map<String, Object> request, @Nullable Map<String, Object> response) {
      return (String) request.get("networkType");
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
      return (String) request.get("serverAddress");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, Object> request) {
      return (Integer) request.get("serverPort");
    }
  }

  @Test
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void normal() {
    Map<String, Object> request = new HashMap<>();
    request.put("method", "POST");
    request.put("urlFull", "https://github.com");
    request.put("urlPath", "/repositories/1");
    request.put("urlQuery", "details=true");
    request.put("urlScheme", "https");
    request.put("header.content-length", "10");
    request.put("route", "/repositories/{id}");
    request.put("header.user-agent", "okhttp 3.x");
    request.put("header.host", "github.com");
    request.put("header.forwarded", "for=1.1.1.1;proto=https");
    request.put("header.custom-request-header", "123,456");
    request.put("networkTransport", "udp");
    request.put("networkType", "ipv4");
    request.put("protocolName", "http");
    request.put("protocolVersion", "2.0");

    Map<String, Object> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("header.content-length", "20");
    response.put("header.custom-response-header", "654,321");

    Function<Context, String> routeFromContext = ctx -> "/repositories/{repoId}";

    AttributesExtractor<Map<String, Object>, Map<String, Object>> extractor =
        HttpServerAttributesExtractor.builder(new TestHttpServerAttributesGetter())
            .setCapturedRequestHeaders(singletonList("Custom-Request-Header"))
            .setCapturedResponseHeaders(singletonList("Custom-Response-Header"))
            .setHttpRouteGetter(routeFromContext)
            .build();

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_HOST_NAME, "github.com"),
            entry(SemanticAttributes.SERVER_ADDRESS, "github.com"),
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_REQUEST_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_SCHEME, "https"),
            entry(SemanticAttributes.HTTP_TARGET, "/repositories/1?details=true"),
            entry(SemanticAttributes.URL_SCHEME, "https"),
            entry(SemanticAttributes.URL_PATH, "/repositories/1"),
            entry(SemanticAttributes.URL_QUERY, "details=true"),
            entry(SemanticAttributes.USER_AGENT_ORIGINAL, "okhttp 3.x"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{id}"),
            entry(SemanticAttributes.HTTP_CLIENT_IP, "1.1.1.1"),
            entry(SemanticAttributes.CLIENT_ADDRESS, "1.1.1.1"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, response, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NET_PROTOCOL_VERSION, "2.0"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{repoId}"),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 10L),
            entry(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, 10L),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, 20L),
            entry(SemanticAttributes.HTTP_RESPONSE_BODY_SIZE, 20L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")));
  }
}
