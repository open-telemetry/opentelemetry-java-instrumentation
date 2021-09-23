/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpServerAttributesExtractorTest {

  static class TestHttpServerAttributesExtractor
      extends HttpServerAttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    protected String method(Map<String, String> request) {
      return request.get("method");
    }

    @Override
    protected String url(Map<String, String> request) {
      return request.get("url");
    }

    @Override
    protected String target(Map<String, String> request) {
      return request.get("target");
    }

    @Override
    protected String host(Map<String, String> request) {
      return request.get("host");
    }

    @Override
    protected String route(Map<String, String> request) {
      return request.get("route");
    }

    @Override
    protected String scheme(Map<String, String> request) {
      return request.get("scheme");
    }

    @Override
    protected String serverName(Map<String, String> request, Map<String, String> response) {
      return request.get("serverName");
    }

    @Override
    protected String userAgent(Map<String, String> request) {
      return request.get("userAgent");
    }

    @Override
    protected Long requestContentLength(Map<String, String> request, Map<String, String> response) {
      return Long.parseLong(request.get("requestContentLength"));
    }

    @Override
    protected Long requestContentLengthUncompressed(
        Map<String, String> request, Map<String, String> response) {
      return Long.parseLong(request.get("requestContentLengthUncompressed"));
    }

    @Override
    protected Integer statusCode(Map<String, String> request, Map<String, String> response) {
      return Integer.parseInt(response.get("statusCode"));
    }

    @Override
    protected String flavor(Map<String, String> request, Map<String, String> response) {
      return request.get("flavor");
    }

    @Override
    protected Long responseContentLength(
        Map<String, String> request, Map<String, String> response) {
      return Long.parseLong(response.get("responseContentLength"));
    }

    @Override
    protected Long responseContentLengthUncompressed(
        Map<String, String> request, Map<String, String> response) {
      return Long.parseLong(response.get("responseContentLengthUncompressed"));
    }
  }

  @Test
  void normal() {
    Map<String, String> request = new HashMap<>();
    request.put("method", "POST");
    request.put("url", "http://github.com");
    request.put("target", "github.com");
    request.put("host", "github.com:80");
    request.put("scheme", "https");
    request.put("userAgent", "okhttp 3.x");
    request.put("requestContentLength", "10");
    request.put("requestContentLengthUncompressed", "11");
    request.put("flavor", "http/2");
    request.put("route", "/repositories/{id}");
    request.put("serverName", "server");

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("responseContentLength", "20");
    response.put("responseContentLengthUncompressed", "21");

    TestHttpServerAttributesExtractor extractor = new TestHttpServerAttributesExtractor();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, request);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.HTTP_TARGET, "github.com"),
            entry(SemanticAttributes.HTTP_HOST, "github.com:80"),
            entry(SemanticAttributes.HTTP_SCHEME, "https"),
            entry(SemanticAttributes.HTTP_USER_AGENT, "okhttp 3.x"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{id}"));

    extractor.onEnd(attributes, request, response, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.HTTP_TARGET, "github.com"),
            entry(SemanticAttributes.HTTP_HOST, "github.com:80"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{id}"),
            entry(SemanticAttributes.HTTP_SCHEME, "https"),
            entry(SemanticAttributes.HTTP_USER_AGENT, "okhttp 3.x"),
            entry(SemanticAttributes.HTTP_ROUTE, "/repositories/{id}"),
            entry(SemanticAttributes.HTTP_SERVER_NAME, "server"),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 10L),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH_UNCOMPRESSED, 11L),
            entry(SemanticAttributes.HTTP_FLAVOR, "http/2"),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, 20L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH_UNCOMPRESSED, 21L));
  }
}
