/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedDeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstrumentationModuleInstallerTest {

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @ParameterizedTest(name = "isInstrumentationEnabled({0}) = {4}")
  @MethodSource("instrumentationEnabledParams")
  void testIsInstrumentationEnabled(
      @SuppressWarnings("unused") String description,
      Boolean firstEnabled,
      Boolean secondEnabled,
      boolean defaultEnabled,
      boolean expected) {

    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean("otel.instrumentation.first.enabled")).thenReturn(firstEnabled);
    when(config.getBoolean("otel.instrumentation.second.enabled")).thenReturn(secondEnabled);

    OpenTelemetry openTelemetry = createOpenTelemetry(config);
    GlobalOpenTelemetry.set(openTelemetry);

    assertThat(
            InstrumentationModuleInstaller.isInstrumentationEnabled(
                new TreeSet<>(asList("first", "second")), defaultEnabled))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> instrumentationEnabledParams() {
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
        Arguments.of("disabled by default, second instrumentation is on", null, true, false, true),
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

  private static OpenTelemetry createOpenTelemetry(ConfigProperties config) {
    ExtendedOpenTelemetry otel = mock(ExtendedOpenTelemetry.class);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    when(otel.getConfigProvider()).thenReturn(configProvider);
    when(configProvider.getInstrumentationConfig())
        .thenReturn(
            ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(config));
    return otel;
  }
}
