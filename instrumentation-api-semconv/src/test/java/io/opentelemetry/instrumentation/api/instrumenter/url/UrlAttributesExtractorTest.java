/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.url;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class UrlAttributesExtractorTest {

  static class TestUrlAttributesGetter implements UrlAttributesGetter<Map<String, String>> {
    @Nullable
    @Override
    public String getFullUrl(Map<String, String> request) {
      return request.get("url");
    }

    @Nullable
    @Override
    public String getUrlScheme(Map<String, String> request) {
      return request.get("scheme");
    }

    @Nullable
    @Override
    public String getUrlPath(Map<String, String> request) {
      return request.get("path");
    }

    @Nullable
    @Override
    public String getUrlQuery(Map<String, String> request) {
      return request.get("query");
    }
  }

  @Test
  void allAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("url", "https://opentelemetry.io/test?q=Java");
    request.put("scheme", "https");
    request.put("path", "/test");
    request.put("query", "q=Java");

    AttributesExtractor<Map<String, String>, Void> extractor =
        UrlAttributesExtractor.create(new TestUrlAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(UrlAttributes.URL_FULL, "https://opentelemetry.io/test?q=Java"),
            entry(UrlAttributes.URL_SCHEME, "https"),
            entry(UrlAttributes.URL_PATH, "/test"),
            entry(UrlAttributes.URL_QUERY, "q=Java"));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  void noAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        UrlAttributesExtractor.create(new TestUrlAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), emptyMap());
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), emptyMap(), null, null);
    assertThat(endAttributes.build()).isEmpty();
  }

  @ParameterizedTest
  @ArgumentsSource(SanitizeUserInfoArguments.class)
  void sanitizeUserInfo(String url, String expectedResult) {
    Map<String, String> request = new HashMap<>();
    request.put("url", url);

    AttributesExtractor<Map<String, String>, Void> extractor =
        UrlAttributesExtractor.create(new TestUrlAttributesGetter());

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build()).containsOnly(entry(UrlAttributes.URL_FULL, expectedResult));
  }

  static final class SanitizeUserInfoArguments implements ArgumentsProvider {

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
}
