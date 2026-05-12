/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultInstrumentationConfigTest {

  private static Stream<Arguments> configPropertyDefaults() {
    return Stream.of(
        Arguments.of(
            "micrometer", "base_time_unit", "s", "otel.instrumentation.micrometer.base-time-unit"),
        Arguments.of(
            "log4j_appender",
            "experimental_log_attributes/development",
            "true",
            "otel.instrumentation.log4j-appender.experimental-log-attributes"),
        Arguments.of(
            "spring_scheduling",
            "controller_telemetry/development",
            "false",
            "otel.instrumentation.spring-scheduling.experimental.controller-telemetry"),
        Arguments.of(
            "grpc",
            "experimental_span_attributes/development",
            "true",
            "otel.instrumentation.grpc.experimental-span-attributes"));
  }

  @ParameterizedTest
  @MethodSource("configPropertyDefaults")
  void toConfigProperties(
      String instrumentation, String key, String value, String expectedPropertyKey) {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get(instrumentation).setDefault(key, value);

    Map<String, String> props = defaults.toConfigProperties();

    assertThat(props).containsEntry(expectedPropertyKey, value).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("configPropertyDefaults")
  void toConfigPropertiesRoundTripsThroughBridge(
      String instrumentation, String key, String value, String expectedPropertyKey) {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get(instrumentation).setDefault(key, value);

    DeclarativeConfigProperties config =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(defaults.toConfigProperties()));

    assertThat(config.getStructured("java").getStructured(instrumentation).getString(key))
        .isEqualTo(value);
  }

  @Test
  void toConfigPropertiesWithCustomMapping() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.addMapping("acme", "acme.full_name");
    defaults.get("acme").get("full_name").setDefault("preserved", "true");

    assertThat(defaults.toConfigProperties()).containsEntry("acme.preserved", "true").hasSize(1);
  }
}
