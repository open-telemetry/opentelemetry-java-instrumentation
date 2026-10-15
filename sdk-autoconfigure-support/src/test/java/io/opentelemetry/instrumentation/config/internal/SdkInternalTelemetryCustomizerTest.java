/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SdkInternalTelemetryCustomizerTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "legacy"})
  void properties(String version) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");
    if (!version.isEmpty()) {
      properties.put("otel.experimental.sdk.telemetry.version", version);
    }
    AutoConfiguredOpenTelemetrySdk configured =
        AutoConfiguredOpenTelemetrySdk.builder().addPropertiesSupplier(() -> properties).build();
    try (OpenTelemetrySdk sdk = configured.getOpenTelemetrySdk()) {
      assertThat(
              AutoConfigureUtil.getConfig(configured)
                  .getString("otel.experimental.sdk.telemetry.version"))
          .isEqualTo(version.isEmpty() ? "latest" : version);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "instrumentation/development:\n  java:\n    other:\n      enabled: true\n"})
  void declarativeDefaultVersion(String instrumentation) {
    String yaml = "file_format: '1.2'\n" + instrumentation;
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model).getSdk()) {
      ConfigProvider configProvider = ((ExtendedOpenTelemetry) sdk).getConfigProvider();
      assertThat(
              configProvider
                  .getInstrumentationConfig("otel_sdk")
                  .getString("internal_telemetry_version"))
          .isEqualTo("latest");
      if (!instrumentation.isEmpty()) {
        assertThat(configProvider.getInstrumentationConfig("other").getBoolean("enabled")).isTrue();
      }
    }
  }

  @Test
  void declarativeExplicitVersion() {
    String yaml =
        "file_format: '1.2'\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    otel_sdk:\n"
            + "      internal_telemetry_version: legacy";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model).getSdk()) {
      assertThat(
              ((ExtendedOpenTelemetry) sdk)
                  .getConfigProvider()
                  .getInstrumentationConfig("otel_sdk")
                  .getString("internal_telemetry_version"))
          .isEqualTo("legacy");
    }
  }
}
