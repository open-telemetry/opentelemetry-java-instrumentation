/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
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

  static class TestHttpClientAttributesExtractor
      extends HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> {

    TestHttpClientAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
      super(capturedHttpHeaders);
    }

    @Override
    protected String method(Map<String, String> request) {
      return request.get("method");
    }

    @Override
    protected String url(Map<String, String> request) {
      return request.get("url");
    }

    @Override
    protected List<String> requestHeader(Map<String, String> request, String name) {
      return asList(request.get("header." + name).split(","));
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

    @Override
    protected List<String> responseHeader(
        Map<String, String> request, Map<String, String> response, String name) {
      return asList(response.get("header." + name).split(","));
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

    TestHttpClientAttributesExtractor extractor =
        new TestHttpClientAttributesExtractor(
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
}
