/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

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
class AlternateUrlSchemeProviderTest {

  private static final String REQUEST = "request";

  @Mock HttpServerAttributesGetter<String, String> getter;

  @InjectMocks AlternateUrlSchemeProvider<String> underTest;

  @Test
  void noHeaders() {
    doReturn(emptyList()).when(getter).getHttpRequestHeader(eq(REQUEST), any());
    assertThat(underTest.apply(REQUEST)).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(ForwardedHeaderValues.class)
  void parseForwardedHeader(String headerValue, String expectedScheme) {
    doReturn(singletonList(headerValue)).when(getter).getHttpRequestHeader(REQUEST, "forwarded");
    assertThat(underTest.apply(REQUEST)).isEqualTo(expectedScheme);
  }

  static final class ForwardedHeaderValues implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          arguments("for=1.1.1.1;proto=xyz", "xyz"),
          arguments("for=1.1.1.1;proto=xyz;", "xyz"),
          arguments("for=1.1.1.1;proto=xyz,", "xyz"),
          arguments("for=1.1.1.1;proto=", null),
          arguments("for=1.1.1.1;proto=;", null),
          arguments("for=1.1.1.1;proto=,", null),
          arguments("for=1.1.1.1;proto=\"xyz\"", "xyz"),
          arguments("for=1.1.1.1;proto=\"xyz\";", "xyz"),
          arguments("for=1.1.1.1;proto=\"xyz\",", "xyz"),
          arguments("for=1.1.1.1;proto=\"", null),
          arguments("for=1.1.1.1;proto=\"\"", null),
          arguments("for=1.1.1.1;proto=\"\";", null),
          arguments("for=1.1.1.1;proto=\"\",", null));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(ForwardedProtoHeaderValues.class)
  void parseForwardedProtoHeader(String headerValue, String expectedScheme) {
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "forwarded");
    doReturn(singletonList(headerValue))
        .when(getter)
        .getHttpRequestHeader(REQUEST, "x-forwarded-proto");
    assertThat(underTest.apply(REQUEST)).isEqualTo(expectedScheme);
  }

  static final class ForwardedProtoHeaderValues implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(arguments("xyz", "xyz"), arguments("\"xyz\"", "xyz"), arguments("\"", null));
    }
  }
}
