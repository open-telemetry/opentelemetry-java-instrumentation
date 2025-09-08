/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenTelemetryInstallerTest {

  @BeforeEach
  @AfterEach
  void setUp() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void globalOpenTelemetry() {
    AutoConfiguredOpenTelemetrySdk sdk =
        OpenTelemetryInstaller.installOpenTelemetrySdk(
            EarlyInitAgentConfig.class.getClassLoader(), EarlyInitAgentConfig.create());

    assertThat(sdk).isNotNull().isNotEqualTo(OpenTelemetry.noop());
  }

  @ParameterizedTest
  @CsvSource({
    "default, true, false",
    "none, false, false",
    ", true, false", // empty value means property is not set
    "invalid, false, true",
  })
  void defaultEnabledInDeclarativeConfigPropertiesBridge(
      String propertyValue, boolean expected, boolean fail) {
    String profile =
        propertyValue == null ? "" : "instrumentation_mode: \"" + propertyValue + "\"";
    String yaml =
        "file_format: \"1.0-rc.1\"\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    agent:\n"
            + "      "
            + profile;

    Supplier<ConfigProperties> configPropertiesSupplier =
        () ->
            OpenTelemetryInstaller.getDeclarativeConfigBridgedProperties(
                EarlyInitAgentConfig.create(),
                SdkConfigProvider.create(
                    DeclarativeConfiguration.parse(
                        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))));

    if (fail) {
      assertThatCode(() -> configPropertiesSupplier.get())
          .isInstanceOf(ConfigurationException.class)
          .hasMessage("Unknown instrumentation profile: invalid");
    } else {
      assertThat(
              configPropertiesSupplier
                  .get()
                  .getBoolean("otel.instrumentation.common.default-enabled"))
          .isEqualTo(expected);
    }
  }
}
