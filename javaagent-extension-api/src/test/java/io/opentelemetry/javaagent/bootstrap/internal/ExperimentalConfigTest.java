/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExperimentalConfigTest {

  @ParameterizedTest
  @ValueSource(strings = {"controller", "view"})
  void telemetryConfigPrefersStableNamesAndIgnoresOldNamesInV3Preview(String telemetry) {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties commonConfig = openTelemetry.getInstrumentationConfig("common");
    String name = telemetry + "_telemetry";
    DeclarativeConfigProperties stable = commonConfig.get(name);
    DeclarativeConfigProperties deprecated = commonConfig.get(name + "/development");

    assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();

    when(stable.getBoolean("enabled")).thenReturn(false);
    when(deprecated.getBoolean("enabled")).thenReturn(true);
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(ExperimentalConfig.class.getName());
    logger.addHandler(handler);
    try {
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isFalse();
      when(stable.getBoolean("enabled")).thenReturn(true);
      assertThat(telemetryEnabled(new ExperimentalConfig(openTelemetry), telemetry)).isTrue();
      assertThat(handler.records).isEmpty();

      when(stable.getBoolean("enabled")).thenReturn(null);
      ExperimentalConfig config = new ExperimentalConfig(openTelemetry);
      assertThat(telemetryEnabled(config, telemetry)).isTrue();
      assertThat(telemetryEnabled(config, telemetry)).isTrue();
      when(deprecated.getBoolean("enabled")).thenReturn(false);
      assertThat(telemetryEnabled(config, telemetry)).isFalse();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .contains(
              "otel.instrumentation.common.experimental." + telemetry + "-telemetry.enabled",
              "otel.instrumentation.common." + telemetry + "-telemetry.enabled");

      when(commonConfig.getBoolean("v3_preview", false)).thenReturn(true);
      clearInvocations(deprecated);
      assertThat(telemetryEnabled(config, telemetry)).isFalse();
      when(stable.getBoolean("enabled")).thenReturn(true);
      assertThat(telemetryEnabled(config, telemetry)).isTrue();
      verifyNoInteractions(deprecated);
      assertThat(handler.records).hasSize(1);
    } finally {
      logger.removeHandler(handler);
    }
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
