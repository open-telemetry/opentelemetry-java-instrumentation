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
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.NetAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
      implements NetServerAttributesGetter<Map<String, Object>> {

    @Nullable
    @Override
    public String getProtocolName(Map<String, Object> request) {
      return (String) request.get("protocolName");
    }

    @Nullable
    @Override
    public String getProtocolVersion(Map<String, Object> request) {
      return (String) request.get("protocolVersion");
    }

    @Nullable
    @Override
    public String getHostName(Map<String, Object> request) {
      return (String) request.get("hostName");
    }

    @Nullable
    @Override
    public Integer getHostPort(Map<String, Object> request) {
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
            entry(SemanticAttributes.NET_HOST_NAME, "github.com"),
            entry(NetAttributes.NET_PROTOCOL_NAME, "http"),
            entry(NetAttributes.NET_PROTOCOL_VERSION, "2.0"),
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_SCHEME, "http"),
            entry(SemanticAttributes.HTTP_TARGET, "/repositories/1?details=true"),
            entry(UrlAttributes.URL_SCHEME, "http"),
            entry(UrlAttributes.URL_PATH, "/repositories/1"),
            entry(UrlAttributes.URL_QUERY, "details=true"),
            entry(SemanticAttributes.USER_AGENT_ORIGINAL, "okhttp 3.x"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{id}"),
            entry(SemanticAttributes.HTTP_CLIENT_IP, "1.1.1.1"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, response, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{repoId}"),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 10L),
            entry(HttpAttributes.HTTP_REQUEST_BODY_SIZE, 10L),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 202L),
            entry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, 20L),
            entry(HttpAttributes.HTTP_RESPONSE_BODY_SIZE, 20L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")));
  }
}
