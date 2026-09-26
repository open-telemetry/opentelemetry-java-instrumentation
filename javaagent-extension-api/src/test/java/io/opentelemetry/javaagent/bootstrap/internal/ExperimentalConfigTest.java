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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
class ExperimentalConfigTest {

  // Check the once-per-property warnings before other tests use deprecated telemetry settings.
  @Order(1)
  @ParameterizedTest
  @ValueSource(strings = {"controller", "view"})
  void telemetryConfigPrefersStableNamesAndCachesValues(String telemetry) {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties commonConfig = openTelemetry.getInstrumentationConfig("common");
    String name = telemetry + "_telemetry";
    DeclarativeConfigProperties stable = commonConfig.get(name);
    DeclarativeConfigProperties deprecated = commonConfig.get(name + "/development");
    when(commonConfig.getBoolean("v3_preview")).thenReturn(true);

    assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();

    when(stable.getBoolean("enabled")).thenReturn(false);
    when(deprecated.getBoolean("enabled")).thenReturn(true);
    clearInvocations(deprecated);
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(ExperimentalConfig.class.getName());
    logger.addHandler(handler);
    try {
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();
      when(stable.getBoolean("enabled")).thenReturn(true);
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isTrue();
      assertThat(handler.records).isEmpty();

      when(stable.getBoolean("enabled")).thenReturn(null);
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();
      verify(deprecated, never()).getBoolean("enabled");
      assertThat(handler.records).isEmpty();

      when(commonConfig.getBoolean("v3_preview")).thenReturn(false);
      ExperimentalConfig config = new ExperimentalConfig(openTelemetry);
      assertThat(telemetryEnabled(config, telemetry)).isTrue();
      when(deprecated.getBoolean("enabled")).thenReturn(false);
      assertThat(telemetryEnabled(config, telemetry)).isTrue();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .contains(
              "otel.instrumentation.common.experimental." + telemetry + "-telemetry.enabled",
              "otel.instrumentation.common." + telemetry + "-telemetry.enabled");

      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();
      when(deprecated.getBoolean("enabled")).thenReturn(true);
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isTrue();
      assertThat(handler.records).hasSize(1);

      when(commonConfig.getBoolean("v3_preview")).thenReturn(true);
      clearInvocations(deprecated);
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();
      verify(deprecated, never()).getBoolean("enabled");
      when(stable.getBoolean("enabled")).thenReturn(true);
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isTrue();
      assertThat(handler.records).hasSize(1);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @MethodSource("telemetryModes")
  void flatTelemetryConfig(String telemetry, boolean v3Preview) {
    Map<String, String> properties = new HashMap<>();
    if (v3Preview) {
      properties.put("otel.instrumentation.common.v3-preview", "true");
    }
    properties.put(
        "otel.instrumentation.common.experimental." + telemetry + "-telemetry.enabled", "true");
    assertThat(telemetryEnabled(new ExperimentalConfig(flatConfig(properties)), telemetry))
        .isEqualTo(!v3Preview);

    properties.put("otel.instrumentation.common." + telemetry + "-telemetry.enabled", "false");
    assertThat(telemetryEnabled(new ExperimentalConfig(flatConfig(properties)), telemetry))
        .isFalse();

    properties.put("otel.instrumentation.common." + telemetry + "-telemetry.enabled", "true");
    assertThat(telemetryEnabled(new ExperimentalConfig(flatConfig(properties)), telemetry))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("telemetryModes")
  void yamlTelemetryConfig(String telemetry, boolean v3Preview) {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + (v3Preview ? "      v3_preview: true\n" : "")
            + "      "
            + telemetry
            + "_telemetry/development:\n"
            + "        enabled: true\n";
    assertThat(telemetryEnabled(new ExperimentalConfig(yamlConfig(yaml)), telemetry))
        .isEqualTo(!v3Preview);

    String stableKey = "      " + telemetry + "_telemetry:\n";
    assertThat(
            telemetryEnabled(
                new ExperimentalConfig(yamlConfig(yaml + stableKey + "        enabled: false\n")),
                telemetry))
        .isFalse();
    assertThat(
            telemetryEnabled(
                new ExperimentalConfig(yamlConfig(yaml + stableKey + "        enabled: true\n")),
                telemetry))
        .isTrue();
  }

  private static Stream<Arguments> telemetryModes() {
    return Stream.of(
        Arguments.of("controller", false),
        Arguments.of("controller", true),
        Arguments.of("view", false),
        Arguments.of("view", true));
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
    when(messaging.get("headers/development").getScalarList("included", String.class))
        .thenReturn(asList("Test-*", "other"));
    when(messaging.get("headers/development").getScalarList("excluded", String.class))
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
    when(messaging.get("headers/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(messaging.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(null);
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

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
