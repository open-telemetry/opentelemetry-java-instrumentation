/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.List;
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

  @Test
  void readsCapturedMessagingHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties messaging =
        openTelemetry.getInstrumentationConfig("common").get("messaging");
    when(messaging.getScalarList("captured_headers", String.class))
        .thenReturn(asList("Test-Header", "*", "Test-?"));

    List<String> headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers).containsExactly("Test-Header", "*", "Test-?");
  }

  @Test
  void absentConfigCapturesNothing() {
    List<String> headers = new ExperimentalConfig(mockOpenTelemetry()).getMessagingHeaders();

    assertThat(headers).isEmpty();
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
    DeclarativeConfigProperties messaging = commonConfig.get("messaging");
    when(messaging.getScalarList("captured_headers", String.class)).thenReturn(null);
    return openTelemetry;
  }
}
