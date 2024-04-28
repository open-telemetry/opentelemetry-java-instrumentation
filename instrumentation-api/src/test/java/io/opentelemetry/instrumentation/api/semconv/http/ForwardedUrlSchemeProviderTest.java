/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForwardedUrlSchemeProviderTest {

  private static final String REQUEST = "request";

  @Mock HttpServerAttributesGetter<String, String> getter;

  @InjectMocks ForwardedUrlSchemeProvider<String> underTest;

  @Test
  void noHeaders() {
    when(getter.getHttpRequestHeader(eq(REQUEST), any())).thenReturn(emptyList());
    assertThat(underTest.apply(REQUEST)).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(ForwardedHeaderValues.class)
  void parseForwardedHeader(List<String> values, String expectedScheme) {
    when(getter.getHttpRequestHeader(REQUEST, "forwarded")).thenReturn(values);
    assertThat(underTest.apply(REQUEST)).isEqualTo(expectedScheme);
  }

  static final class ForwardedHeaderValues implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          arguments(singletonList("for=1.1.1.1;proto=xyz"), "xyz"),
          arguments(singletonList("for=1.1.1.1;proto=xyz;"), "xyz"),
          arguments(singletonList("for=1.1.1.1;proto=xyz,"), "xyz"),
          arguments(singletonList("for=1.1.1.1;proto="), null),
          arguments(singletonList("for=1.1.1.1;proto=;"), null),
          arguments(singletonList("for=1.1.1.1;proto=,"), null),
          arguments(singletonList("for=1.1.1.1;proto=\"xyz\""), "xyz"),
          arguments(singletonList("for=1.1.1.1;proto=\"xyz\";"), "xyz"),
          arguments(singletonList("for=1.1.1.1;proto=\"xyz\","), "xyz"),
          arguments(singletonList("for=1.1.1.1;proto=\""), null),
          arguments(singletonList("for=1.1.1.1;proto=\"\""), null),
          arguments(singletonList("for=1.1.1.1;proto=\"\";"), null),
          arguments(singletonList("for=1.1.1.1;proto=\"\","), null),
          arguments(asList("for=1.1.1.1", "proto=xyz", "proto=abc"), "xyz"));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(ForwardedProtoHeaderValues.class)
  @SuppressWarnings("MockitoDoSetup")
  void parseForwardedProtoHeader(List<String> values, String expectedScheme) {
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "forwarded");
    doReturn(values).when(getter).getHttpRequestHeader(REQUEST, "x-forwarded-proto");
    assertThat(underTest.apply(REQUEST)).isEqualTo(expectedScheme);
  }

  static final class ForwardedProtoHeaderValues implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          arguments(singletonList("xyz"), "xyz"),
          arguments(singletonList("\"xyz\""), "xyz"),
          arguments(singletonList("\""), null),
          arguments(asList("xyz", "abc"), "xyz"));
    }
  }
}
