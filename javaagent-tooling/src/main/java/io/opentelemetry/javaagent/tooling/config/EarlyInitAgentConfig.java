/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import java.util.Locale;
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

  private static String toEnvVarName(String propertyName) {
    return propertyName.toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_');
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

  // visible for testing
  @Nullable
  String getString(String propertyName) {
    String value = System.getProperty(propertyName);
    if (value == null) {
      value = System.getenv(toEnvVarName(propertyName));
    }
    if (value == null) {
      value = configFileContents.get(propertyName);
    }
    return value;
  }

  // visible for testing
  boolean getBoolean(String propertyName, boolean defaultValue) {
    String value = getString(propertyName);
    return value != null ? Boolean.parseBoolean(value) : defaultValue;
  }

  private int getInt(String propertyName, int defaultValue) {
    try {
      String value = getString(propertyName);
      if (value != null) {
        return Integer.parseInt(value);
      }
      return defaultValue;
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }

  public void logEarlyConfigErrorsIfAny() {
    ConfigurationFile.logErrorIfAny();
  }
}
