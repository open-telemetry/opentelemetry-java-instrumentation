/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExperimentalConfigTest {

  @ParameterizedTest
  @ValueSource(strings = {"controller", "view"})
  void flatTelemetryConfig(String telemetry) {
    Map<String, String> properties = new HashMap<>();
    assertThat(telemetryEnabled(new ExperimentalConfig(flatConfig(properties)), telemetry))
        .isFalse();

    properties.put("otel.instrumentation.common." + telemetry + "-telemetry.enabled", "false");
    assertThat(telemetryEnabled(new ExperimentalConfig(flatConfig(properties)), telemetry))
        .isFalse();
    properties.put("otel.instrumentation.common." + telemetry + "-telemetry.enabled", "true");
    assertThat(telemetryEnabled(new ExperimentalConfig(flatConfig(properties)), telemetry))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"controller", "view"})
  void yamlTelemetryConfig(String telemetry) {
    String yaml =
        "file_format: 1.1\n" + "instrumentation/development:\n" + "  java:\n" + "    common:\n";
    assertThat(
            telemetryEnabled(
                new ExperimentalConfig(
                    yamlConfig(yaml + "      " + telemetry + "_telemetry: {}\n")),
                telemetry))
        .isFalse();

    String telemetryConfig = yaml + "      " + telemetry + "_telemetry:\n";
    assertThat(
            telemetryEnabled(
                new ExperimentalConfig(yamlConfig(telemetryConfig + "        enabled: false\n")),
                telemetry))
        .isFalse();
    assertThat(
            telemetryEnabled(
                new ExperimentalConfig(yamlConfig(telemetryConfig + "        enabled: true\n")),
                telemetry))
        .isTrue();
  }

  private static ExtendedOpenTelemetry flatConfig(Map<String, String> properties) {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry.getInstrumentationConfig("common"))
        .thenReturn(
            DeclarativeConfigBridge.createInstrumentationConfig(
                    DefaultConfigProperties.createFromMap(properties))
                .getInstrumentationConfig()
                .getStructured("java")
                .getStructured("common"));

    return openTelemetry;
  }

  private static ExtendedOpenTelemetry yamlConfig(String yaml) {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry.getInstrumentationConfig("common"))
        .thenReturn(
            SdkConfigProvider.create(
                    DeclarativeConfiguration.toConfigProperties(
                        DeclarativeConfiguration.parse(
                            new ByteArrayInputStream(yaml.getBytes(UTF_8)))))
                .getInstrumentationConfig()
                .getStructured("java")
                .getStructured("common"));

    return openTelemetry;
  }

  @Test
  void readsMessagingHeaderSelector() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties messaging =
        openTelemetry.getInstrumentationConfig("common").get("messaging");
    when(messaging.get("headers").getScalarList("included", String.class))
        .thenReturn(asList("Test-*", "other"));
    when(messaging.get("headers").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));

    IncludeExclude headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers.getIncluded()).containsExactly("Test-*", "other");
    assertThat(headers.getExcluded()).containsExactly("*-secret");
  }

  @Test
  void fallsBackToDeprecatedCaptureHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry
            .getInstrumentationConfig("common")
            .get("messaging")
            .getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    IncludeExclude headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers.getIncluded()).containsExactly("deprecated");
    assertThat(headers.getExcluded()).isEmpty();
  }

  @Test
  void v3PreviewUsesStableMessagingHeadersWithoutDeprecatedFallback() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(true);
    DeclarativeConfigProperties messaging =
        openTelemetry.getInstrumentationConfig("common").get("messaging");
    when(messaging.get("headers").getScalarList("included", String.class))
        .thenReturn(singletonList("Test-*"));
    when(messaging.getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    IncludeExclude headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers.matches("Test-public")).isTrue();
    assertThat(headers.matches("deprecated")).isFalse();
  }

  @Test
  void absentConfigCapturesNothing() {
    IncludeExclude headers = new ExperimentalConfig(mockOpenTelemetry()).getMessagingHeaders();

    assertThat(headers.isEmpty()).isTrue();
  }

  private static boolean telemetryEnabled(ExperimentalConfig config, String telemetry) {
    return telemetry.equals("controller")
        ? config.controllerTelemetryEnabled()
        : config.viewTelemetryEnabled();
  }

  private static ExtendedOpenTelemetry mockOpenTelemetry() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    DeclarativeConfigProperties deprecatedMessagingConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("messaging")).thenReturn(deprecatedMessagingConfig);
    DeclarativeConfigProperties messaging = commonConfig.get("messaging");
    when(messaging.get("headers").getScalarList("included", String.class)).thenReturn(null);
    when(messaging.get("headers").getScalarList("excluded", String.class)).thenReturn(null);
    when(messaging.getScalarList("capture_headers/development", String.class)).thenReturn(null);
    when(deprecatedMessagingConfig
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(null);
    when(deprecatedMessagingConfig
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(null);
    when(deprecatedMessagingConfig.getScalarList("capture_headers/development", String.class))
        .thenReturn(null);
    return openTelemetry;
  }
}
