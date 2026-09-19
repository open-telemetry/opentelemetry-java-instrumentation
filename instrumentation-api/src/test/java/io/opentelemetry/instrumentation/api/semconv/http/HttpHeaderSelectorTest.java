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
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the HTTP header selectors applied by {@link HttpCommonAttributesExtractor}. */
class HttpHeaderSelectorTest {

  private static final Map<String, String> REQUEST = new LinkedHashMap<>();
  private static final Map<String, String> RESPONSE = new LinkedHashMap<>();

  static {
    REQUEST.put("Test-Request-Header", "one");
    REQUEST.put("Test-Request-Other", "two");
    REQUEST.put("Authorization", "secret");

    RESPONSE.put("Test-Response-Header", "three");
    RESPONSE.put("Test-Response-Other", "four");
    RESPONSE.put("Set-Cookie", "secret");
  }

  @Test
  void capturesExactIncludedNames() {
    assertThat(captureRequest(selector(singletonList("Test-Request-Header"), emptyList())))
        .containsOnly(requestHeaderEntry("test-request-header", "one"));
    assertThat(captureResponse(selector(singletonList("Test-Response-Header"), emptyList())))
        .containsOnly(responseHeaderEntry("test-response-header", "three"));
  }

  @Test
  void capturesWildcardIncludedNames() {
    assertThat(captureRequest(selector(singletonList("Test-Request-*"), emptyList())))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            requestHeaderEntry("test-request-other", "two"));
    assertThat(captureResponse(selector(singletonList("Test-Response-*"), emptyList())))
        .containsOnly(
            responseHeaderEntry("test-response-header", "three"),
            responseHeaderEntry("test-response-other", "four"));
  }

  @Test
  void capturesSingleCharacterWildcardIncludedNames() {
    assertThat(captureRequest(selector(singletonList("Test-Request-Othe?"), emptyList())))
        .containsOnly(requestHeaderEntry("test-request-other", "two"));
  }

  @Test
  void matchesHeaderNamesCaseInsensitively() {
    assertThat(captureRequest(selector(singletonList("TEST-REQUEST-HEADER"), emptyList())))
        .containsOnly(requestHeaderEntry("test-request-header", "one"));
    assertThat(captureRequest(selector(singletonList("test-request-*"), emptyList())))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            requestHeaderEntry("test-request-other", "two"));
    assertThat(captureRequest(selector(singletonList("*"), singletonList("AUTHORIZATION"))))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            requestHeaderEntry("test-request-other", "two"));
  }

  @Test
  void exclusionTakesPrecedenceOverInclusion() {
    assertThat(captureRequest(selector(singletonList("*"), singletonList("Authorization"))))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            requestHeaderEntry("test-request-other", "two"));
    assertThat(
            captureRequest(
                selector(
                    asList("Test-Request-Header", "Authorization"),
                    singletonList("Authorization"))))
        .containsOnly(requestHeaderEntry("test-request-header", "one"));
  }

  @Test
  void excludeOnlySelectorCapturesEveryOtherHeader() {
    assertThat(captureResponse(selector(emptyList(), singletonList("Set-Cookie"))))
        .containsOnly(
            responseHeaderEntry("test-response-header", "three"),
            responseHeaderEntry("test-response-other", "four"));
  }

  @Test
  void emptySelectorCapturesNothing() {
    assertThat(captureRequest(selector(emptyList(), emptyList()))).isEmpty();
    assertThat(captureResponse(selector(emptyList(), emptyList()))).isEmpty();
  }

  @Test
  void absentSelectorCapturesNothing() {
    assertThat(captureRequest(null)).isEmpty();
    assertThat(captureResponse(null)).isEmpty();
  }

  @Test
  void matchingHeaderNameIsCapturedOnlyOnce() {
    assertThat(
            captureRequest(selector(asList("Test-Request-Header", "Test-Request-*"), emptyList())))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            requestHeaderEntry("test-request-other", "two"));
  }

  @Test
  void exactNamesAreCapturedWithoutHeaderNameEnumeration() {
    // simulates a third party getter that implements only getHttpRequestHeader
    assertThat(
            capture(
                new MapGetter(false),
                selector(singletonList("Test-Request-Header"), emptyList()),
                null))
        .containsOnly(requestHeaderEntry("test-request-header", "one"));
  }

  @Test
  void wildcardCapturesNothingWithoutHeaderNameEnumeration() {
    // a third party getter that implements only getHttpRequestHeader cannot resolve wildcards
    assertThat(
            capture(
                new MapGetter(false), selector(singletonList("Test-Request-*"), emptyList()), null))
        .isEmpty();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSettersSelectExactNames() {
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpServerAttributesExtractor.builder(new MapGetter(true))
            .setCapturedRequestHeaders(singletonList("Test-Request-Header"))
            .setCapturedResponseHeaders(singletonList("Test-Response-Header"))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), REQUEST);
    extractor.onEnd(attributes, Context.root(), REQUEST, RESPONSE, null);

    assertThat(headerAttributes(attributes.build()))
        .containsOnly(
            requestHeaderEntry("test-request-header", "one"),
            responseHeaderEntry("test-response-header", "three"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSettersMatchHeaderNamesLiterally() {
    Map<String, String> request = new LinkedHashMap<>(REQUEST);
    request.put("*", "literal");

    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpServerAttributesExtractor.builder(new MapGetter(true))
            .setCapturedRequestHeaders(singletonList("*"))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(headerAttributes(attributes.build()))
        .containsOnly(requestHeaderEntry("*", "literal"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSettersOnlyCapturePresentLiteralNames() {
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpServerAttributesExtractor.builder(new MapGetter(true))
            .setCapturedRequestHeaders(asList("Test-Request-Header", "Test-Request-*", "Test-?"))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), REQUEST);

    assertThat(headerAttributes(attributes.build()))
        .containsOnly(requestHeaderEntry("test-request-header", "one"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSettersCaptureListedNames() {
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor =
        HttpServerAttributesExtractor.builder(new MapGetter(true))
            .setCapturedRequestHeaders(singletonList("Test-Request-Header"))
            .build();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), REQUEST);

    assertThat(headerAttributes(attributes.build()))
        .containsOnly(requestHeaderEntry("test-request-header", "one"));
  }

  private static Map<AttributeKey<?>, Object> captureRequest(IncludeExclude headers) {
    return capture(new MapGetter(true), headers, null);
  }

  private static Map<AttributeKey<?>, Object> captureResponse(IncludeExclude headers) {
    return capture(new MapGetter(true), null, headers);
  }

  private static Map<AttributeKey<?>, Object> capture(
      MapGetter getter, IncludeExclude requestHeaders, IncludeExclude responseHeaders) {
    HttpServerAttributesExtractorBuilder<Map<String, String>, Map<String, String>> builder =
        HttpServerAttributesExtractor.builder(getter);
    if (requestHeaders != null) {
      builder.setRequestHeaders(requestHeaders);
    }
    if (responseHeaders != null) {
      builder.setResponseHeaders(responseHeaders);
    }
    AttributesExtractor<Map<String, String>, Map<String, String>> extractor = builder.build();

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

  private static IncludeExclude selector(List<String> included, List<String> excluded) {
    return IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build();
  }

  private static Map.Entry<AttributeKey<?>, Object> requestHeaderEntry(String name, String value) {
    return new SimpleEntry<>(stringArrayKey("http.request.header." + name), singletonList(value));
  }

  private static Map.Entry<AttributeKey<?>, Object> responseHeaderEntry(String name, String value) {
    return new SimpleEntry<>(stringArrayKey("http.response.header." + name), singletonList(value));
  }

  private static final class MapGetter
      implements HttpServerAttributesGetter<Map<String, String>, Map<String, String>> {

    private final boolean enumerateNames;

    MapGetter(boolean enumerateNames) {
      this.enumerateNames = enumerateNames;
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
    public Collection<String> getHttpRequestHeaderNames(Map<String, String> request) {
      return headerNames(request);
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

    @Override
    public Collection<String> getHttpResponseHeaderNames(
        Map<String, String> request, Map<String, String> response) {
      return headerNames(response);
    }

    private Collection<String> headerNames(Map<String, String> message) {
      return enumerateNames ? new ArrayList<>(message.keySet()) : emptyList();
    }

    // the extractor always looks headers up by their lowercase name, which mirrors the
    // case-insensitive lookup that real HTTP libraries provide
    private static List<String> headerValue(Map<String, String> message, String name) {
      for (Map.Entry<String, String> entry : message.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return singletonList(entry.getValue());
        }
      }
      return emptyList();
    }
  }
}
