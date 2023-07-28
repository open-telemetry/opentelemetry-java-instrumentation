/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.function.BiPredicate;
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
class ServerSideServerPortConditionTest {

  @Mock HttpServerAttributesGetter<String, String> getter;

  @ParameterizedTest
  @ArgumentsSource(Parameters.class)
  void testCondition(Integer port, String scheme, boolean expectedResult) {
    when(getter.getUrlScheme("request")).thenReturn(scheme);
    BiPredicate<Integer, String> condition = new ServerSideServerPortCondition<>(getter);
    assertThat(condition.test(port, "request")).isEqualTo(expectedResult);
  }

  static final class Parameters implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          arguments(80, "http", false),
          arguments(80, "HTTP", false),
          arguments(443, "https", false),
          arguments(443, "HTTPs", false),
          arguments(80, "https", true),
          arguments(443, "http", true),
          arguments(null, "http", true),
          arguments(null, "https", true));
    }
  }
}
