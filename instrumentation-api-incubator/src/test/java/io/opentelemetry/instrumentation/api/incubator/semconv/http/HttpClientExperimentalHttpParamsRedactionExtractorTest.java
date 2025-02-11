/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.UrlAttributes;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpClientExperimentalHttpParamsRedactionExtractorTest {

  @Mock HttpClientAttributesGetter<String, String> httpClientAttributesGetter;

  @ParameterizedTest
  @ArgumentsSource(StripUrlArgumentSource.class)
  void shouldRedactUrlQueryParameters(String url, String expectedResult) {

    // given
    when(httpClientAttributesGetter.getUrlFull(any())).thenReturn(url);

    HttpClientExperimentalHttpParamsRedactionExtractor<String, String> extractor =
        HttpClientExperimentalHttpParamsRedactionExtractor.create(httpClientAttributesGetter);

    AttributesBuilder attributesBuilder = Attributes.builder();
    Context context = Context.root();

    // when
    extractor.onStart(attributesBuilder, context, "request");

    // then
    Attributes attributes = attributesBuilder.build();
    assertThat(attributes).containsOnly(entry(UrlAttributes.URL_FULL, expectedResult));
  }

  static final class StripUrlArgumentSource implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          arguments("https://github.com/p@th?foo=b@r", "https://github.com/p@th?foo=b@r"),
          arguments("https://github.com#t@st.html", "https://github.com#t@st.html"),
          arguments("https://github.com@", "https://github.com@"),
          arguments(
              "https://service.com?paramA=valA&paramB=valB",
              "https://service.com?paramA=valA&paramB=valB"),
          arguments(
              "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7",
              "https://service.com?AWSAccessKeyId=REDACTED"),
          arguments(
              "https://service.com?Signature=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0%3A377",
              "https://service.com?Signature=REDACTED"),
          arguments(
              "https://service.com?sig=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0",
              "https://service.com?sig=REDACTED"),
          arguments(
              "https://service.com?X-Goog-Signature=39Up9jzHkxhuIhFE9594DJxe7w6cIRCg0V6ICGS0",
              "https://service.com?X-Goog-Signature=REDACTED"),
          arguments(
              "https://service.com?paramA=valA&AWSAccessKeyId=AKIAIOSFODNN7&paramB=valB",
              "https://service.com?paramA=valA&AWSAccessKeyId=REDACTED&paramB=valB"),
          arguments(
              "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&paramA=valA",
              "https://service.com?AWSAccessKeyId=REDACTED&paramA=valA"),
          arguments(
              "https://service.com?paramA=valA&AWSAccessKeyId=AKIAIOSFODNN7",
              "https://service.com?paramA=valA&AWSAccessKeyId=REDACTED"),
          arguments(
              "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7&AWSAccessKeyId=ZGIAIOSFODNN7",
              "https://service.com?AWSAccessKeyId=REDACTED&AWSAccessKeyId=REDACTED"),
          arguments(
              "https://service.com?AWSAccessKeyId=AKIAIOSFODNN7#ref",
              "https://service.com?AWSAccessKeyId=REDACTED#ref"));
    }
  }
}
