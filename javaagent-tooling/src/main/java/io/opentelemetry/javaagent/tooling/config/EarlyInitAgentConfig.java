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
public final class EarlyInitAgentConfig {

  private static final EarlyInitAgentConfig INSTANCE =
      new EarlyInitAgentConfig(ConfigurationFile.getProperties());

  public static EarlyInitAgentConfig get() {
    return INSTANCE;
  }

  private final Map<String, String> configFileContents;

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.configFileContents = configFileContents;
  }

  @Nullable
  public String getLogging() {
    return getString("otel.javaagent.logging");
  }

  @Nullable
  public String getExtensions() {
    return getString("otel.javaagent.extensions");
  }

  public boolean isDebug() {
    return getBoolean("otel.javaagent.debug", false);
  }

  public boolean isEnabled() {
    return getBoolean("otel.javaagent.enabled", true);
  }

  public boolean isExperimentalFieldInjectionEnabled() {
    return getBoolean("otel.javaagent.experimental.field-injection.enabled", true);
  }

  public int getLoggingApplicationLogsBufferMaxRecords() {
    return getInt("otel.javaagent.logging.application.logs-buffer-max-records", 2048);
  }

  @Nullable
  private String getString(String propertyName) {
    String value = ConfigPropertiesUtil.getString(propertyName);
    if (value != null) {
      return value;
    }
    return configFileContents.get(propertyName);
  }

  private boolean getBoolean(String propertyName, boolean defaultValue) {
    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return ConfigPropertiesUtil.getBoolean(propertyName, configFileValue);
  }

  private int getInt(String propertyName, int defaultValue) {
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
}
