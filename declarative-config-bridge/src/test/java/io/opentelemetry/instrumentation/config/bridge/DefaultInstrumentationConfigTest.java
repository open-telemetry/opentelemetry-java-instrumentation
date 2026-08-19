/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpClientInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultInstrumentationConfigTest {

  @Test
  void setDefaultOnRootNodeRejected() {
    assertThatThrownBy(() -> new DefaultInstrumentationConfig().setDefault("some_key", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("defaults must be set below an instrumentation node, e.g. get(\"micrometer\")");
  }

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
                    defaults.get("common").get("http").setDefault("known_methods", "GET,POST"),
            "otel.instrumentation.http.known-methods",
            "GET,POST"),
        Arguments.of(
            "list default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("common")
                        .get("http")
                        .setDefault("known_methods", asList("GET", "POST")),
            "otel.instrumentation.http.known-methods",
            "GET,POST"),
        Arguments.of(
            "general default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults -> setGeneralClientRequestHeaders(defaults, "X-Request-Id"),
            "otel.instrumentation.http.client.capture-request-headers",
            "X-Request-Id"));
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
  void toConfigPropertiesRoundTripsListThroughBridge() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("common").get("http").setDefault("known_methods", asList("GET", "POST"));

    DeclarativeConfigProperties config =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(defaults.toConfigProperties()));

    assertThat(
            config
                .getStructured("java")
                .getStructured("common")
                .getStructured("http")
                .getScalarList("known_methods", String.class))
        .containsExactly("GET", "POST");
  }

  @Test
  void toConfigPropertiesRoundTripsGeneralDefaultThroughBridge() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    setGeneralClientRequestHeaders(defaults, "X-Request-Id");

    DeclarativeConfigProperties config =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(defaults.toConfigProperties()));

    assertThat(
            config
                .getStructured("general")
                .getStructured("http")
                .getStructured("client")
                .getScalarList("request_captured_headers", String.class))
        .containsExactly("X-Request-Id");
  }

  @Test
  void generalNamedJavaInstrumentationRemainsUnderJava() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("general").setDefault("enabled", true);

    assertThat(defaults.toConfigProperties())
        .containsEntry("otel.instrumentation.general.enabled", "true")
        .hasSize(1);
  }

  @Test
  void toConfigPropertiesWithCustomMapping() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.addMapping("acme", "acme.full_name");
    defaults.get("acme").get("full_name").setDefault("preserved", "true");

    assertThat(defaults.toConfigProperties()).containsEntry("acme.preserved", "true").hasSize(1);
  }

  private static void setGeneralClientRequestHeaders(
      DefaultInstrumentationConfig defaults, String header) {
    defaults.customizeGeneral(
        general ->
            general.withHttp(
                new ExperimentalHttpInstrumentationModel()
                    .withClient(
                        new ExperimentalHttpClientInstrumentationModel()
                            .withRequestCapturedHeaders(asList(header)))));
  }
}
