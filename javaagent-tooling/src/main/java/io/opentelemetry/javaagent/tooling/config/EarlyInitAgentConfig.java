/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.javaagent.extension.DeclarativeConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class EarlyInitAgentConfig {

  public static EarlyInitAgentConfig create() {
    String configurationFile =
        DefaultConfigProperties.create(Collections.emptyMap())
            .getString("otel.experimental.config.file");

    return configurationFile != null
        ? new EarlyInitAgentConfig(loadConfigurationModel(configurationFile))
        : new EarlyInitAgentConfig(ConfigurationFile.getProperties());
  }

  private final Map<String, String> configFileContents;
  private final @Nullable OpenTelemetryConfigurationModel configurationModel;
  private final @Nullable ConfigProvider configProvider;
  private final @Nullable ConfigProperties declarativeConfigProperties;

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.configFileContents = configFileContents;
    this.configurationModel = null;
    this.configProvider = null;
    this.declarativeConfigProperties = null;
  }

  private EarlyInitAgentConfig(@Nonnull OpenTelemetryConfigurationModel configurationModel) {
    this.configFileContents = null;
    this.configurationModel = configurationModel;
    this.configProvider = SdkConfigProvider.create(configurationModel);
    this.declarativeConfigProperties = new DeclarativeConfigPropertiesBridge(this.configProvider);
  }

  @Nullable
  public OpenTelemetryConfigurationModel getConfigurationModel() {
    return configurationModel;
  }

  @Nullable
  public ConfigProvider getConfigProvider() {
    return configProvider;
  }

  @Nullable
  public ConfigProperties getDeclarativeConfigProperties() {
    return declarativeConfigProperties;
  }

  @Nullable
  public String getString(String propertyName) {
    if (declarativeConfigProperties != null) {
      return declarativeConfigProperties.getString(propertyName);
    }
    String value = ConfigPropertiesUtil.getString(propertyName);
    if (value != null) {
      return value;
    }
    return configFileContents.get(propertyName);
  }

  public boolean getBoolean(String propertyName, boolean defaultValue) {
    if (declarativeConfigProperties != null) {
      return declarativeConfigProperties.getBoolean(propertyName, defaultValue);
    }

    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return ConfigPropertiesUtil.getBoolean(propertyName, configFileValue);
  }

  public int getInt(String propertyName, int defaultValue) {
    if (declarativeConfigProperties != null) {
      return declarativeConfigProperties.getInt(propertyName, defaultValue);
    }
    try {
      String configFileValueStr = configFileContents.get(propertyName);
      int configFileValue =
          configFileValueStr == null ? defaultValue : Integer.parseInt(configFileValueStr);
      return ConfigPropertiesUtil.getInt(propertyName, configFileValue);
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }

  public void logEarlyConfigErrorsIfAny() {
    ConfigurationFile.logErrorIfAny();
  }

  private static OpenTelemetryConfigurationModel loadConfigurationModel(String configurationFile) {
    try (FileInputStream fis = new FileInputStream(configurationFile)) {
      return DeclarativeConfiguration.parse(fis);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
