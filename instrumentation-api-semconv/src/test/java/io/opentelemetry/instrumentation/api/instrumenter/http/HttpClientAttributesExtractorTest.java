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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    public Integer statusCode(
        Map<String, String> request, Map<String, String> response, @Nullable Throwable error) {
      return Integer.parseInt(response.get("statusCode"));
    }

    @Override
    public String flavor(Map<String, String> request, Map<String, String> response) {
      return request.get("flavor");
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
    request.put("header.content-length", "10");
    request.put("flavor", "http/2");
    request.put("header.user-agent", "okhttp 3.x");
    request.put("header.custom-request-header", "123,456");

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("header.content-length", "20");
    response.put("header.custom-response-header", "654,321");

    HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter())
            .setCapturedRequestHeaders(singletonList("Custom-Request-Header"))
            .setCapturedResponseHeaders(singletonList("Custom-Response-Header"))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.HTTP_USER_AGENT, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")));

    extractor.onEnd(attributes, Context.root(), request, response, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.HTTP_USER_AGENT, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")),
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 10L),
            entry(SemanticAttributes.HTTP_FLAVOR, "http/2"),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, 20L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")));
  }
  @ParameterizedTest
  @MethodSource("stripUrlArguments")
  public void stripBasicAuthTest(String url, String expectedResult) {
    Map<String, String> request = new HashMap<>();
    request.put("url", url);

    stripRequestTest(request, expectedResult);
  }

  private static Stream<Arguments> stripUrlArguments() {
    return Stream.of(
        arguments("https://user1:secret@github.com", "https://github.com"),
        arguments("https://user1:secret@github.com?foo=b@r", "https://github.com?foo=b@r"),
        arguments("user1:secret@github.com", "github.com")
    );
  }
  @Test
  public void stripBasicAuthWithQueryParamTest() {
    Map<String, String> request = new HashMap<>();
    request.put("url", "https://user1:secret@github.com?foo=b@r");

    stripRequestTest(request, "https://github.com?foo=b@r");
  }

  @Test
  public void stripBasicAuthWithoutSchemeTest() {
    Map<String, String> request = new HashMap<>();
    request.put("url", "user1:secret@github.com");

    stripRequestTest(request, "github.com");
  }

  private static void stripRequestTest(Map<String, String> request, String expected) {
    HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter()).build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build()).containsOnly(entry(SemanticAttributes.HTTP_URL, expected));
  }

  @Test
  void invalidStatusCode() {
    Map<String, String> request = new HashMap<>();

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "0");

    HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter())
            .setCapturedRequestHeaders(emptyList())
            .setCapturedResponseHeaders(emptyList())
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    assertThat(attributes.build()).isEmpty();

    extractor.onEnd(attributes, Context.root(), request, response, null);
    assertThat(attributes.build()).isEmpty();
  }
}
