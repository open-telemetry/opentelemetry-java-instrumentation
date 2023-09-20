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
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.HashSet;
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
import org.junit.jupiter.params.provider.ValueSource;

class HttpClientAttributesExtractorStableSemconvTest {

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
      String value = response.get("statusCode");
      return value == null ? null : Integer.parseInt(value);
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
      String value = request.get("serverPort");
      return value == null ? null : Integer.parseInt(value);
    }

    @Nullable
    @Override
    public String getErrorType(
        Map<String, String> request,
        @Nullable Map<String, String> respobse,
        @Nullable Throwable error) {
      return request.get("errorType");
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
    request.put("networkTransport", "udp");
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
            entry(SemanticAttributes.HTTP_REQUEST_METHOD, "POST"),
            entry(SemanticAttributes.URL_FULL, "http://github.com"),
            entry(SemanticAttributes.USER_AGENT_ORIGINAL, "okhttp 3.x"),
            entry(
                AttributeKey.stringArrayKey("http.request.header.custom_request_header"),
                asList("123", "456")),
            entry(SemanticAttributes.SERVER_ADDRESS, "github.com"),
            entry(SemanticAttributes.SERVER_PORT, 123L),
            entry(SemanticAttributes.HTTP_RESEND_COUNT, 2L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, response, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, 10L),
            entry(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 202L),
            entry(SemanticAttributes.HTTP_RESPONSE_BODY_SIZE, 20L),
            entry(
                AttributeKey.stringArrayKey("http.response.header.custom_response_header"),
                asList("654", "321")),
            entry(SemanticAttributes.NETWORK_TRANSPORT, "udp"),
            entry(SemanticAttributes.NETWORK_TYPE, "ipv4"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "1.1"));
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
    request.put("networkTransport", observedTransport);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    if (extractedTransport != null) {
      assertThat(attributes.build())
          .containsEntry(SemanticAttributes.NETWORK_TRANSPORT, extractedTransport);
    } else {
      assertThat(attributes.build()).doesNotContainKey(SemanticAttributes.NETWORK_TRANSPORT);
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

  @ParameterizedTest
  @ArgumentsSource(ValidRequestMethodsProvider.class)
  void shouldExtractKnownMethods(String requestMethod) {
    Map<String, String> request = new HashMap<>();
    request.put("method", requestMethod);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    assertThat(attributes.build())
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD, requestMethod)
        .doesNotContainKey(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"get", "Get"})
  void shouldTreatMethodsAsCaseSensitive(String requestMethod) {
    Map<String, String> request = new HashMap<>();
    request.put("method", requestMethod);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    assertThat(attributes.build())
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD, HttpConstants._OTHER)
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL, requestMethod);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PURGE", "not a method really"})
  void shouldUseOtherForUnknownMethods(String requestMethod) {
    Map<String, String> request = new HashMap<>();
    request.put("method", requestMethod);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    assertThat(attributes.build())
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD, HttpConstants._OTHER)
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL, requestMethod);
  }

  @ParameterizedTest
  @ValueSource(strings = {"only", "custom", "methods", "allowed"})
  void shouldExtractKnownMethods_override(String requestMethod) {
    Map<String, String> request = new HashMap<>();
    request.put("method", requestMethod);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter())
            .setKnownMethods(new HashSet<>(asList("only", "custom", "methods", "allowed")))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    assertThat(attributes.build())
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD, requestMethod)
        .doesNotContainKey(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL);
  }

  @ParameterizedTest
  @ArgumentsSource(ValidRequestMethodsProvider.class)
  void shouldUseOtherForUnknownMethods_override(String requestMethod) {
    Map<String, String> request = new HashMap<>();
    request.put("method", requestMethod);

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.builder(new TestHttpClientAttributesGetter())
            .setKnownMethods(new HashSet<>(asList("only", "custom", "methods", "allowed")))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    assertThat(attributes.build())
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD, HttpConstants._OTHER)
        .containsEntry(SemanticAttributes.HTTP_REQUEST_METHOD_ORIGINAL, requestMethod);
  }

  @Test
  void shouldExtractErrorType_httpStatusCode() {
    Map<String, String> response = new HashMap<>();
    response.put("statusCode", "400");

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), emptyMap());
    extractor.onEnd(attributes, Context.root(), emptyMap(), response, null);

    assertThat(attributes.build())
        .containsEntry(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 400)
        .containsEntry(HttpAttributes.ERROR_TYPE, "400");
  }

  @Test
  void shouldExtractErrorType_getter() {
    Map<String, String> request = new HashMap<>();
    request.put("statusCode", "0");
    request.put("errorType", "custom error type");

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), emptyMap());
    extractor.onEnd(attributes, Context.root(), request, emptyMap(), null);

    assertThat(attributes.build()).containsEntry(HttpAttributes.ERROR_TYPE, "custom error type");
  }

  @Test
  void shouldExtractErrorType_exceptionClassName() {
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), emptyMap());
    extractor.onEnd(attributes, Context.root(), emptyMap(), emptyMap(), new ConnectException());

    assertThat(attributes.build())
        .containsEntry(HttpAttributes.ERROR_TYPE, "java.net.ConnectException");
  }

  @Test
  void shouldExtractErrorType_other() {
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpClientAttributesExtractor.create(new TestHttpClientAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), emptyMap());
    extractor.onEnd(attributes, Context.root(), emptyMap(), emptyMap(), null);

    assertThat(attributes.build()).containsEntry(HttpAttributes.ERROR_TYPE, HttpConstants._OTHER);
  }
}
