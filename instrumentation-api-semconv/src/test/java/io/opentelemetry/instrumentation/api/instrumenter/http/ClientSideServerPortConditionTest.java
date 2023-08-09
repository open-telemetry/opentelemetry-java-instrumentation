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
class ClientSideServerPortConditionTest {

  @Mock HttpClientAttributesGetter<String, String> getter;

  @ParameterizedTest
  @ArgumentsSource(Parameters.class)
  void testCondition(Integer port, String url, boolean expectedResult) {
    when(getter.getUrlFull("request")).thenReturn(url);
    BiPredicate<Integer, String> condition = new ClientSideServerPortCondition<>(getter);
    assertThat(condition.test(port, "request")).isEqualTo(expectedResult);
  }

  static final class Parameters implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          arguments(80, "http://test", false),
          arguments(80, "HTTP://TEST", false),
          arguments(443, "https://test", false),
          arguments(443, "HTTPs://TEST", false),
          arguments(80, "https://test", true),
          arguments(443, "http://test", true),
          arguments(null, "http://test", true),
          arguments(null, "https://test", true));
    }
  }
}
