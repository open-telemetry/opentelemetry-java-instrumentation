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
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpClientAttributesExtractorTest {

  static class TestHttpClientAttributesGetter
      implements HttpClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String method(Map<String, String> request) {
      return request.get("method");
    }

    @Override
    public String url(Map<String, String> request) {
      return request.get("url");
    }

    @Override
    public List<String> requestHeader(Map<String, String> request, String name) {
      String value = request.get("header." + name);
      return value == null ? emptyList() : asList(value.split(","));
    }

    @Override
    public Long requestContentLength(Map<String, String> request, Map<String, String> response) {
      String value = request.get("requestContentLength");
      return value == null ? null : Long.parseLong(value);
    }

    @Override
    public Long requestContentLengthUncompressed(
        Map<String, String> request, Map<String, String> response) {
      String value = request.get("requestContentLengthUncompressed");
      return value == null ? null : Long.parseLong(value);
    }

    @Override
    public Integer statusCode(Map<String, String> request, Map<String, String> response) {
      return Integer.parseInt(response.get("statusCode"));
    }

    @Override
    public String flavor(Map<String, String> request, Map<String, String> response) {
      return request.get("flavor");
    }

    @Override
    public Long responseContentLength(Map<String, String> request, Map<String, String> response) {
      String value = response.get("responseContentLength");
      return value == null ? null : Long.parseLong(value);
    }

    @Override
    public Long responseContentLengthUncompressed(
        Map<String, String> request, Map<String, String> response) {
      String value = response.get("responseContentLengthUncompressed");
      return value == null ? null : Long.parseLong(value);
    }

    @Override
    public List<String> responseHeader(
        Map<String, String> request, Map<String, String> response, String name) {
      String value = response.get("header." + name);
      return value == null ? emptyList() : asList(value.split(","));
    }
  }

  @Test
  void normal() {
    Map<String, String> request = new HashMap<>();
    request.put("method", "POST");
    request.put("url", "http://github.com");
    request.put("requestContentLength", "10");
    request.put("requestContentLengthUncompressed", "11");
    request.put("flavor", "http/2");
    request.put("header.user-agent", "okhttp 3.x");
    request.put("header.custom-request-header", "123,456");

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("responseContentLength", "20");
    response.put("responseContentLengthUncompressed", "21");
    response.put("header.custom-response-header", "654,321");

    HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(
            new TestHttpClientAttributesGetter(),
            CapturedHttpHeaders.create(
                singletonList("Custom-Request-Header"), singletonList("Custom-Response-Header")));

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, request);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.HTTP_USER_AGENT, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")));

    extractor.onEnd(attributes, request, response, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.HTTP_USER_AGENT, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 10L),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH_UNCOMPRESSED, 11L),
            entry(SemanticAttributes.HTTP_FLAVOR, "http/2"),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, 20L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH_UNCOMPRESSED, 21L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")));
  }

  @Test
  void invalidStatusCode() {
    Map<String, String> request = new HashMap<>();

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "0");

    HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(
            new TestHttpClientAttributesGetter(), CapturedHttpHeaders.empty());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, request);
    assertThat(attributes.build()).isEmpty();

    extractor.onEnd(attributes, request, response, null);
    assertThat(attributes.build()).isEmpty();
  }
}
