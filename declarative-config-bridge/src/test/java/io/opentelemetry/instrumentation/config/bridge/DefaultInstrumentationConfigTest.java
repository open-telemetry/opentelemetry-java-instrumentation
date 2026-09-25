/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpClientInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalHttpInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.List;
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
        argumentSet(
            "string default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults -> defaults.get("micrometer").setDefault("base_time_unit", "s"),
            "otel.instrumentation.micrometer.base-time-unit",
            "s"),
        argumentSet(
            "boolean experimental default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("log4j_appender")
                        .setDefault("experimental_log_attributes/development", true),
            "otel.instrumentation.log4j-appender.experimental-log-attributes",
            "true"),
        argumentSet(
            "boolean non-experimental development default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("spring_scheduling")
                        .setDefault("controller_telemetry/development", false),
            "otel.instrumentation.spring-scheduling.experimental.controller-telemetry",
            "false"),
        argumentSet(
            "experimental special mapping",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults.get("common").get("http").setDefault("known_methods", "GET,POST"),
            "otel.instrumentation.http.known-methods",
            "GET,POST"),
        argumentSet(
            "list default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults
                        .get("common")
                        .get("http")
                        .setDefault("known_methods", asList("GET", "POST")),
            "otel.instrumentation.http.known-methods",
            "GET,POST"),
        argumentSet(
            "general default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults -> setGeneralClientRequestHeaders(defaults, "X-Request-Id"),
            "otel.instrumentation.http.client.capture-request-headers",
            "X-Request-Id"),
        argumentSet(
            "semconv stability opt-in default",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults.customizeGeneral(general -> general.setStabilityOptInList("http")),
            "otel.semconv-stability.opt-in",
            "http"));
  }

  @ParameterizedTest
  @MethodSource("configPropertyDefaults")
  void toConfigProperties(
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

    assertThat(config.get("java").get("common").get("http").getString("known_methods"))
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
                .get("java")
                .get("common")
                .get("http")
                .getScalarList("known_methods", String.class))
        .containsExactly("GET", "POST");
  }

  @Test
  void toConfigPropertiesRoundTripsServicePeerMappingThroughBridge() {
    Map<String, String> servicePeerMapping = new HashMap<>();
    servicePeerMapping.put("peer", "example.com");
    servicePeerMapping.put("service_name", "checkout");
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("common").setDefault("service_peer_mapping", singletonList(servicePeerMapping));

    Map<String, String> properties = defaults.toConfigProperties();

    assertThat(properties)
        .containsEntry("otel.instrumentation.common.peer-service-mapping", "example.com=checkout");
    DeclarativeConfigProperties config =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(properties));
    List<DeclarativeConfigProperties> roundTripped =
        config.get("java").get("common").getStructuredList("service_peer_mapping");
    assertThat(roundTripped).hasSize(1);
    assertThat(roundTripped.get(0).getString("peer")).isEqualTo("example.com");
    assertThat(roundTripped.get(0).getString("service_name")).isEqualTo("checkout");
  }

  @Test
  void toConfigPropertiesRejectsUnsupportedStructuredList() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.get("acme").setDefault("structured", singletonList(singletonList("value")));

    assertThatThrownBy(defaults::toConfigProperties)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "structured list default has no traditional config serialization: acme.structured");
  }

  private static Stream<Arguments> emptyListDefaults() {
    return Stream.of(
        argumentSet(
            "java instrumentation scalar list",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults.get("common").get("http").setDefault("known_methods", emptyList()),
            "common.http.known_methods"),
        argumentSet(
            "general instrumentation scalar list",
            (Consumer<DefaultInstrumentationConfig>)
                defaults ->
                    defaults.customizeGeneral(
                        general ->
                            general.setHttp(
                                new ExperimentalHttpInstrumentationModel()
                                    .setClient(
                                        new ExperimentalHttpClientInstrumentationModel()
                                            .setRequestCapturedHeaders(emptyList())))),
            "general.http.client.request_captured_headers"));
  }

  @ParameterizedTest
  @MethodSource("emptyListDefaults")
  void toConfigPropertiesRejectsEmptyListDefaults(
      Consumer<DefaultInstrumentationConfig> customizer, String path) {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    customizer.accept(defaults);

    assertThatThrownBy(defaults::toConfigProperties)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("list default must not be empty: " + path);
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
                .get("general")
                .get("http")
                .get("client")
                .getScalarList("request_captured_headers", String.class))
        .containsExactly("X-Request-Id");
  }

  @Test
  void toConfigPropertiesRoundTripsSemconvStabilityOptInThroughBridge() {
    DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
    defaults.customizeGeneral(general -> general.setStabilityOptInList("http"));

    DeclarativeConfigProperties config =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(defaults.toConfigProperties()));

    assertThat(config.get("general").getString("stability_opt_in_list")).isEqualTo("http");
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

  private static Stream<Arguments> conflictingDefaults() {
    return Stream.of(
        argumentSet(
            "parent then child",
            (Consumer<DefaultInstrumentationConfig>)
                defaults -> {
                  defaults.get("acme").setDefault("name", "parent");
                  defaults.get("acme").get("name").setDefault("first", "child");
                }),
        argumentSet(
            "child then parent",
            (Consumer<DefaultInstrumentationConfig>)
                defaults -> {
                  defaults.get("acme").get("name").setDefault("first", "child");
                  defaults.get("acme").setDefault("name", "parent");
                }));
  }

  @ParameterizedTest
  @MethodSource("conflictingDefaults")
  void setDefaultRejectsPrefixCollisions(Consumer<DefaultInstrumentationConfig> customizer) {
    assertThatThrownBy(() -> customizer.accept(new DefaultInstrumentationConfig()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("default path conflicts with existing default:")
        .hasMessageContaining("acme.name")
        .hasMessageContaining("acme.name.first");
  }

  private static void setGeneralClientRequestHeaders(
      DefaultInstrumentationConfig defaults, String header) {
    defaults.customizeGeneral(
        general ->
            general.setHttp(
                new ExperimentalHttpInstrumentationModel()
                    .setClient(
                        new ExperimentalHttpClientInstrumentationModel()
                            .setRequestCapturedHeaders(singletonList(header)))));
  }
}
