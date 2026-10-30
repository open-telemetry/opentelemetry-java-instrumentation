/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for exact-name HTTP header capture by {@link HttpCommonAttributesExtractor}. */
class HttpHeaderSelectorTest {

  private static final Map<String, String> REQUEST = new LinkedHashMap<>();
  private static final Map<String, String> RESPONSE = new LinkedHashMap<>();

  static {
    REQUEST.put("Test-Request-Header", "one");
    REQUEST.put("Test-Request-Other", "two");
    REQUEST.put("Authorization", "secret");
    REQUEST.put("*", "literal-star");
    REQUEST.put("Test-?", "literal-question");
    REQUEST.put("Test-Multiple", "one,two");

    RESPONSE.put("Test-Response-Header", "three");
    RESPONSE.put("Test-Response-Other", "four");
    RESPONSE.put("Set-Cookie", "secret");
    RESPONSE.put("*", "literal-star");
    RESPONSE.put("Test-?", "literal-question");
    RESPONSE.put("Test-Multiple", "three,four");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void capturesOnlyExactNamesCaseInsensitively(boolean client) {
    assertThat(
            capture(
                client,
                new LinkedHashSet<>(asList("TEST-REQUEST-HEADER", "test-request-header")),
                singletonList("TEST-RESPONSE-HEADER")))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            responseHeaderEntry("test-response-header", "three"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void absentListsCaptureNothing(boolean client) {
    assertThat(capture(client, null, null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyListsCaptureNothing(boolean client) {
    assertThat(capture(client, emptyList(), emptyList())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void capturesLiteralStarAndQuestionMarkWithoutExpansion(boolean client) {
    assertThat(capture(client, asList("*", "Test-?"), asList("*", "Test-?")))
        .containsOnly(
            requestHeaderEntry("*", "literal-star"),
            requestHeaderEntry("test-?", "literal-question"),
            responseHeaderEntry("*", "literal-star"),
            responseHeaderEntry("test-?", "literal-question"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void doesNotExpandPatterns(boolean client) {
    assertThat(
            capture(
                client,
                asList("Test-Request-*", "Test-Request-Othe?"),
                asList("Test-Response-*", "Test-Response-Othe?")))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesMultipleValues(boolean client) {
    assertThat(capture(client, singletonList("Test-Multiple"), singletonList("Test-Multiple")))
        .containsOnly(
            new SimpleEntry<>(
                stringArrayKey("http.request.header.test-multiple"), asList("one", "two")),
            new SimpleEntry<>(
                stringArrayKey("http.response.header.test-multiple"), asList("three", "four")));
  }

  private static Map<AttributeKey<?>, Object> capture(
      boolean client, Collection<String> requestHeaders, Collection<String> responseHeaders) {
    MapGetter getter = new MapGetter();
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor;
    if (client) {
      HttpClientAttributesExtractorBuilder<Map<String, String>, Map<String, String>> builder =
          HttpClientAttributesExtractor.builder(getter);
      if (requestHeaders != null) {
        builder.setRequestCapturedHeaders(requestHeaders);
      }
      if (responseHeaders != null) {
        builder.setResponseCapturedHeaders(responseHeaders);
      }
      extractor = builder.build();
    } else {
      HttpServerAttributesExtractorBuilder<Map<String, String>, Map<String, String>> builder =
          HttpServerAttributesExtractor.builder(getter);
      if (requestHeaders != null) {
        builder.setRequestCapturedHeaders(requestHeaders);
      }
      if (responseHeaders != null) {
        builder.setResponseCapturedHeaders(responseHeaders);
      }
      extractor = builder.build();
    }

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), REQUEST);
    extractor.onEnd(attributes, Context.root(), REQUEST, RESPONSE, null);
    return headerAttributes(attributes.build());
  }

  private static Map<AttributeKey<?>, Object> headerAttributes(Attributes attributes) {
    Map<AttributeKey<?>, Object> headers = new LinkedHashMap<>();
    attributes.forEach(
        (key, value) -> {
          if (key.getKey().startsWith("http.request.header.")
              || key.getKey().startsWith("http.response.header.")) {
            headers.put(key, value);
          }
        });
    return headers;
  }

  private static Map.Entry<AttributeKey<?>, Object> requestHeaderEntry(String name, String value) {
    return new SimpleEntry<>(stringArrayKey("http.request.header." + name), singletonList(value));
  }

  private static Map.Entry<AttributeKey<?>, Object> responseHeaderEntry(String name, String value) {
    return new SimpleEntry<>(stringArrayKey("http.response.header." + name), singletonList(value));
  }

  private static final class MapGetter
      implements HttpServerAttributesGetter<Map<String, String>, Map<String, String>>,
          HttpClientAttributesGetter<Map<String, String>, Map<String, String>> {

    @Override
    public String getUrlFull(Map<String, String> request) {
      return "http://localhost/";
    }

    @Override
    public String getServerAddress(Map<String, String> request) {
      return "localhost";
    }

    @Override
    public Integer getServerPort(Map<String, String> request) {
      return 80;
    }

    @Override
    public String getErrorType(
        Map<String, String> request, Map<String, String> response, Throwable error) {
      return null;
    }

    @Override
    public String getHttpRequestMethod(Map<String, String> request) {
      return "GET";
    }

    @Override
    public String getUrlPath(Map<String, String> request) {
      return "/";
    }

    @Override
    public String getUrlQuery(Map<String, String> request) {
      return null;
    }

    @Override
    public String getUrlScheme(Map<String, String> request) {
      return "http";
    }

    @Override
    public List<String> getHttpRequestHeader(Map<String, String> request, String name) {
      return headerValue(request, name);
    }

    @Override
    public Integer getHttpResponseStatusCode(
        Map<String, String> request, Map<String, String> response, Throwable error) {
      return 200;
    }

    @Override
    public List<String> getHttpResponseHeader(
        Map<String, String> request, Map<String, String> response, String name) {
      return headerValue(response, name);
    }

    // the extractor always looks headers up by their lowercase name, which mirrors the
    // case-insensitive lookup that real HTTP libraries provide
    private static List<String> headerValue(Map<String, String> message, String name) {
      for (Map.Entry<String, String> entry : message.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return asList(entry.getValue().split(","));
        }
      }
      return emptyList();
    }
  }
}
