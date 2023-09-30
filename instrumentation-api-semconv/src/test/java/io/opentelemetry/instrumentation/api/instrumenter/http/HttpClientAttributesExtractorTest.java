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
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
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

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class HttpClientAttributesExtractorTest {

  static class TestHttpClientAttributesGetter
      implements HttpClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String getUrlFull(Map<String, String> request) {
      return request.get("urlFull");
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
      return Integer.parseInt(response.get("statusCode"));
    }

    @Override
    public List<String> getHttpResponseHeader(
        Map<String, String> request, Map<String, String> response, String name) {
      String value = response.get("header." + name);
      return value == null ? emptyList() : asList(value.split(","));
    }

    @Nullable
    @Override
    public String getNetworkTransport(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("networkTransport");
    }

    @Nullable
    @Override
    public String getNetworkType(
        Map<String, String> request, @Nullable Map<String, String> response) {
      return request.get("networkType");
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
      return request.get("serverAddress");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, String> request) {
      String statusCode = request.get("serverPort");
      return statusCode == null ? null : Integer.parseInt(statusCode);
    }
  }

  @Test
  void normal() {
    Map<String, String> request = new HashMap<>();
    request.put("method", "POST");
    request.put("urlFull", "http://github.com");
    request.put("header.content-length", "10");
    request.put("header.user-agent", "okhttp 3.x");
    request.put("header.custom-request-header", "123,456");
    request.put("networkTransport", "tcp");
    request.put("networkType", "ipv4");
    request.put("protocolName", "http");
    request.put("protocolVersion", "1.1");
    request.put("serverAddress", "github.com");
    request.put("serverPort", "123");

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "202");
    response.put("header.content-length", "20");
    response.put("header.custom-response-header", "654,321");

    ToIntFunction<Context> resendCountFromContext = context -> 2;

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter())
            .setCapturedRequestHeaders(singletonList("Custom-Request-Header"))
            .setCapturedResponseHeaders(singletonList("Custom-Response-Header"))
            .setResendCountIncrementer(resendCountFromContext)
            .build();

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "POST"),
            entry(SemanticAttributes.HTTP_URL, "http://github.com"),
            entry(SemanticAttributes.USER_AGENT_ORIGINAL, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L),
            entry(SemanticAttributes.HTTP_RESEND_COUNT, 2L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, response, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 10L),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, 20L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")),
            entry(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"));
  }

  @ParameterizedTest
  @ArgumentsSource(StripUrlArgumentSource.class)
  void stripBasicAuthTest(String url, String expectedResult) {
    Map<String, String> request = new HashMap<>();
    request.put("urlFull", url);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build()).containsOnly(entry(SemanticAttributes.HTTP_URL, expectedResult));
  }

  static final class StripUrlArgumentSource implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          arguments("https://user1:secret@github.com", "https://REDACTED:REDACTED@github.com"),
          arguments(
              "https://user1:secret@github.com/path/",
              "https://REDACTED:REDACTED@github.com/path/"),
          arguments(
              "https://user1:secret@github.com#test.html",
              "https://REDACTED:REDACTED@github.com#test.html"),
          arguments(
              "https://user1:secret@github.com?foo=b@r",
              "https://REDACTED:REDACTED@github.com?foo=b@r"),
          arguments(
              "https://user1:secret@github.com/p@th?foo=b@r",
              "https://REDACTED:REDACTED@github.com/p@th?foo=b@r"),
          arguments("https://github.com/p@th?foo=b@r", "https://github.com/p@th?foo=b@r"),
          arguments("https://github.com#t@st.html", "https://github.com#t@st.html"),
          arguments("user1:secret@github.com", "user1:secret@github.com"),
          arguments("https://github.com@", "https://github.com@"));
    }
  }

  @Test
  void invalidStatusCode() {
    Map<String, String> request = new HashMap<>();

    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "0");

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    assertThat(attributes.build()).isEmpty();

    extractor.onEnd(attributes, Context.root(), request, response, null);
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  void extractNetPeerNameAndPortFromHostHeader() {
    Map<String, String> request = new HashMap<>();
    request.put("header.host", "thehost:777");

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "thehost"),
            entry(SemanticAttributes.NET_PEER_PORT, 777L));
  }

  @Test
  void extractNetHostAndPortFromNetAttributesGetter() {
    Map<String, String> request = new HashMap<>();
    request.put("header.host", "notthehost:77777"); // this should have lower precedence
    request.put("serverAddress", "thehost");
    request.put("serverPort", "777");

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_PEER_NAME, "thehost"),
            entry(SemanticAttributes.NET_PEER_PORT, 777L));
  }

  @ParameterizedTest
  @ArgumentsSource(DefaultPeerPortArgumentSource.class)
  void defaultPeerPort(int peerPort, String url) {
    Map<String, String> request = new HashMap<>();
    request.put("urlFull", url);
    request.put("serverPort", String.valueOf(peerPort));

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build()).doesNotContainKey(SemanticAttributes.NET_PEER_PORT);
  }

  static class DefaultPeerPortArgumentSource implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          arguments(80, "http://github.com"),
          arguments(80, "HTTP://GITHUB.COM"),
          arguments(443, "https://github.com"),
          arguments(443, "HTTPS://GITHUB.COM"));
    }
  }

  @Test
  void zeroResends() {
    Map<String, String> request = new HashMap<>();

    ToIntFunction<Context> resendCountFromContext = context -> 0;

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter())
            .setResendCountIncrementer(resendCountFromContext)
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, null, null);
    assertThat(attributes.build()).isEmpty();
  }
}
