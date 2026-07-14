/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultInstrumentationConfigTest {

  private static Stream<Arguments> configPropertyDefaults() {
    return Stream.of(
        Arguments.of(
            "string default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults -> defaults.get("micrometer").setDefault("base_time_unit", "s"),
            "otel.instrumentation.micrometer.base-time-unit",
            "s"),
        Arguments.of(
            "boolean experimental default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("log4j_appender")
                        .setDefault("experimental_log_attributes/development", true),
            "otel.instrumentation.log4j-appender.experimental-log-attributes",
            "true"),
        Arguments.of(
            "boolean non-experimental development default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("spring_scheduling")
                        .setDefault("controller_telemetry/development", false),
            "otel.instrumentation.spring-scheduling.experimental.controller-telemetry",
            "false"),
        Arguments.of(
            "experimental special mapping",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("common")
                        .get("http")
                        .setDefault("known_methods", "GET,POST"),
            "otel.instrumentation.http.known-methods",
            "GET,POST"));
  }

  @ParameterizedTest
  @MethodSource("configPropertyDefaults")
  void toConfigProperties(
      String name,
      Consumer<DefaultInstrumentationConfig> defaultsCustomizer,
      String expectedPropertyKey,
      String expectedValue) {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaultsCustomizer.accept(defaults);

    Map<String, String> props = defaults.toConfigProperties();

    assertThat(props).containsEntry(expectedPropertyKey, expectedValue).hasSize(1);
  }

  @Test
  void toConfigPropertiesRoundTripsSpecialMappingThroughBridge() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("common").get("http").setDefault("known_methods", "GET,POST");

    DeclarativeConfigProperties config =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(defaults.toConfigProperties()));

    assertThat(
            config
                .getStructured("java")
                .getStructured("common")
                .getStructured("http")
                .getString("known_methods"))
        .isEqualTo("GET,POST");
  }

  @Test
  void toConfigPropertiesWithCustomMapping() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.addMapping("acme", "acme.full_name");
    defaults.get("acme").get("full_name").setDefault("preserved", "true");

    assertThat(defaults.toConfigProperties()).containsEntry("acme.preserved", "true").hasSize(1);
  }
}
