/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class LegacyConfigFileEarlyInitAgentConfig implements EarlyInitAgentConfig {

  public static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";

  private final Map<String, String> configFileContents;

  LegacyConfigFileEarlyInitAgentConfig() {
    this.configFileContents = ConfigurationFile.getProperties();
  }

  @Override
  public boolean isAgentEnabled() {
    return getBoolean(JAVAAGENT_ENABLED_CONFIG, true);
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    String value = ConfigPropertiesUtil.getString(propertyName);
    if (value != null) {
      return value;
    }
    return configFileContents.get(propertyName);
  }

  @Override
  public boolean getBoolean(String propertyName, boolean defaultValue) {
    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return ConfigPropertiesUtil.getBoolean(propertyName, configFileValue);
  }

  @Override
  public int getInt(String propertyName, int defaultValue) {
    try {
      String configFileValueStr = configFileContents.get(propertyName);
      int configFileValue =
          configFileValueStr == null ? defaultValue : Integer.parseInt(configFileValueStr);
      return ConfigPropertiesUtil.getInt(propertyName, configFileValue);
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }

  @Override
  public void logEarlyConfigErrorsIfAny() {
    ConfigurationFile.logErrorIfAny();
  }
}
