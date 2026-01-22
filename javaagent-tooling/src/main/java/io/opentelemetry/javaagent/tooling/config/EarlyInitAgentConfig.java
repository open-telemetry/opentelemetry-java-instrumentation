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

  static final String CONFIGURATION_FILE_PROPERTY = "otel.javaagent.configuration-file";

  private static final EarlyInitAgentConfig INSTANCE =
      new EarlyInitAgentConfig(ConfigurationFile.getProperties());

  public static EarlyInitAgentConfig get() {
    return INSTANCE;
  }

  private final Map<String, String> configFileContents;

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.configFileContents = configFileContents;
  }

  /**
   * Returns the configuration file path set via system property or environment variable.
   *
   * <p>We cannot use {@link #get()}, because that would lead to infinite recursion.
   */
  @Nullable
  static String getConfigurationFile() {
    return getStringProperty(CONFIGURATION_FILE_PROPERTY);
  }

  /**
   * Returns the boolean value of the given property name from system properties and environment
   * variables.
   */
  static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
    String value = getStringProperty(propertyName);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
  }

  /**
   * Returns the string value of the given property name from system properties and environment
   * variables.
   */
  @Nullable
  static String getStringProperty(String propertyName) {
    String value = System.getProperty(propertyName);
    if (value != null) {
      return value;
    }
    return System.getenv(toEnvVarName(propertyName));
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

  @Nullable
  private String getString(String propertyName) {
    String value = getStringProperty(propertyName);
    if (value != null) {
      return value;
    }
    return configFileContents.get(propertyName);
  }

  private boolean getBoolean(String propertyName, boolean defaultValue) {
    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return getBooleanProperty(propertyName, configFileValue);
  }

  private int getInt(String propertyName, int defaultValue) {
    try {
      String value = getStringProperty(propertyName);
      if (value != null) {
        return Integer.parseInt(value);
      }
      String configFileValueStr = configFileContents.get(propertyName);
      if (configFileValueStr != null) {
        return Integer.parseInt(configFileValueStr);
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
