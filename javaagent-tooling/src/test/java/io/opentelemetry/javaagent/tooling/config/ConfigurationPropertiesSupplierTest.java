/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

@ClearSystemProperty(key = ConfigurationFile.CONFIGURATION_FILE_PROPERTY)
class ConfigurationPropertiesSupplierTest {

  @BeforeEach
  @AfterEach
  void setUp() {
    GlobalOpenTelemetry.resetForTest();
    ConfigurationFile.resetForTest();
  }

  // regression for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6696
  @SetSystemProperty(key = "otel.sdk.disabled", value = "true") // don't setup the SDK
  @Test
  void fileConfigOverwritesUserPropertiesSupplier(@TempDir Path tempDir) throws IOException {
    // given
    Path configFile = tempDir.resolve("test-config.properties");
    Files.write(configFile, singleton("otel.instrumentation.custom.key = 42"));
    System.setProperty(ConfigurationFile.CONFIGURATION_FILE_PROPERTY, configFile.toString());

    // when
    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        OpenTelemetryInstaller.installOpenTelemetrySdk(
            this.getClass().getClassLoader(), EarlyInitAgentConfig.create());

    // then
    assertThat(
            ((ExtendedOpenTelemetry) autoConfiguredSdk.getOpenTelemetrySdk())
                .getConfigProvider()
                .getInstrumentationConfig()
                .getStructured("custom", DeclarativeConfigProperties.empty())
                .getString("key"))
        .isEqualTo("42");
  }

  // baseline for the test above to make sure UserCustomPropertiesSupplier
  // is actually working
  @SetSystemProperty(key = "otel.sdk.disabled", value = "true") // don't setup the SDK
  @Test
  void userPropertiesSupplier(@TempDir Path tempDir) throws IOException {
    // when
    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        OpenTelemetryInstaller.installOpenTelemetrySdk(
            this.getClass().getClassLoader(), EarlyInitAgentConfig.create());

    // then
    assertThat(
            ((ExtendedOpenTelemetry) autoConfiguredSdk.getOpenTelemetrySdk())
                .getConfigProvider()
                .getInstrumentationConfig()
                .getStructured("custom", DeclarativeConfigProperties.empty())
                .getString("key"))
        .isEqualTo("42");
  }

  // SPI used in test
  public static class UserCustomPropertiesSupplier implements AutoConfigurationCustomizerProvider {

    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
      autoConfiguration.addPropertiesSupplier(
          () -> singletonMap("otel.instrumentation.custom.key", "123"));
    }
  }
}
