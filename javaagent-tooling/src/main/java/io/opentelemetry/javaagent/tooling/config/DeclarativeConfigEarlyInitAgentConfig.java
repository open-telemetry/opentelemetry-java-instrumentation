/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.javaagent.extension.DeclarativeConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.io.FileInputStream;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class DeclarativeConfigEarlyInitAgentConfig implements EarlyInitAgentConfig {
  private final OpenTelemetryConfigurationModel configurationModel;
  private final ConfigProvider configProvider;
  private final ConfigProperties declarativeConfigProperties;

  DeclarativeConfigEarlyInitAgentConfig(String configurationFile) {
    this.configurationModel = loadConfigurationModel(configurationFile);
    this.configProvider = SdkConfigProvider.create(configurationModel);
    this.declarativeConfigProperties = new DeclarativeConfigPropertiesBridge(this.configProvider);
  }

  public OpenTelemetryConfigurationModel getConfigurationModel() {
    return configurationModel;
  }

  public ConfigProvider getConfigProvider() {
    return configProvider;
  }

  public ConfigProperties getDeclarativeConfigProperties() {
    return declarativeConfigProperties;
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    return declarativeConfigProperties.getString(propertyName);
  }

  @Override
  public boolean getBoolean(String propertyName, boolean defaultValue) {
    return declarativeConfigProperties.getBoolean(propertyName, defaultValue);
  }

  @Override
  public int getInt(String propertyName, int defaultValue) {
      return declarativeConfigProperties.getInt(propertyName, defaultValue);
   }

  @Override
  public void logEarlyConfigErrorsIfAny() {
    // todo
  }

  private static OpenTelemetryConfigurationModel loadConfigurationModel(String configurationFile) {
    try (FileInputStream fis = new FileInputStream(configurationFile)) {
      return DeclarativeConfiguration.parse(fis);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
