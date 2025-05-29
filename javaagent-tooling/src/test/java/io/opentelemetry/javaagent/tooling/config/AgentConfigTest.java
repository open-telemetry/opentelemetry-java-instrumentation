/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class AgentConfigTest {

  @ParameterizedTest(name = "isInstrumentationEnabled({0}) = {4}")
  @ArgumentsSource(InstrumentationEnabledParams.class)
  void testIsInstrumentationEnabled(
      @SuppressWarnings("unused") String description,
      Boolean firstEnabled,
      Boolean secondEnabled,
      boolean defaultEnabled,
      boolean expected) {

    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean("otel.instrumentation.first.enabled")).thenReturn(firstEnabled);
    when(config.getBoolean("otel.instrumentation.second.enabled")).thenReturn(secondEnabled);

    assertEquals(
        expected,
        AgentConfig.isInstrumentationEnabled(
            config, new TreeSet<>(asList("first", "second")), defaultEnabled));
  }

  private static class InstrumentationEnabledParams implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "enabled by default, both instrumentations are off", false, false, true, false),
          Arguments.of("enabled by default, first instrumentation is on", true, null, true, true),
          Arguments.of("enabled by default, second instrumentation is on", null, true, true, true),
          Arguments.of("enabled by default, both instrumentations are on", true, true, true, true),
          Arguments.of(
              "enabled by default, first instrumentation is off, second is on",
              false,
              true,
              true,
              false),
          Arguments.of(
              "enabled by default, first instrumentation is on, second is off",
              true,
              false,
              true,
              true),
          Arguments.of("enabled by default", null, null, true, true),
          Arguments.of(
              "disabled by default, both instrumentations are off", false, false, false, false),
          Arguments.of("disabled by default, first instrumentation is on", true, null, false, true),
          Arguments.of(
              "disabled by default, second instrumentation is on", null, true, false, true),
          Arguments.of("disabled by default, both instrumentation are on", true, true, false, true),
          Arguments.of(
              "disabled by default, first instrumentation is off, second is on",
              false,
              true,
              false,
              false),
          Arguments.of(
              "disabled by default, first instrumentation is on, second is off",
              true,
              false,
              false,
              true),
          Arguments.of("disabled by default", null, null, false, false));
    }
  }
}
