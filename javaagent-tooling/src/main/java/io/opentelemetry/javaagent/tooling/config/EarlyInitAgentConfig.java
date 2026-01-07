/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class EarlyInitAgentConfig {

  @Nullable private final String logging;
  @Nullable private final String extensions;
  private final boolean enabled;
  private final boolean debug;
  private final int loggingApplicationLogsBufferMaxRecords;
  private final boolean fieldInjectionEnabled;

  public static EarlyInitAgentConfig create() {
    if (DeclarativeConfigurationFile.isConfigured()) {
      return new EarlyInitAgentConfig(DeclarativeConfigurationFile.getProperties());
    }
    return new EarlyInitAgentConfig(ConfigurationFile.getProperties());
  }

  private EarlyInitAgentConfig(@Nullable DeclarativeConfigProperties properties) {
    DeclarativeConfigProperties agent =
        (properties != null ? properties : empty())
            .getStructured("instrumentation/development", empty())
            .getStructured("java", empty())
            .getStructured("agent", empty());

    this.logging = agent.getString("logging");
    this.extensions = agent.getString("extensions");
    this.enabled = agent.getBoolean("enabled", true);
    this.debug = agent.getBoolean("debug", false);
    this.loggingApplicationLogsBufferMaxRecords =
        agent
            .getStructured("logging", empty())
            .getStructured("application", empty())
            .getInt("logs_buffer_max_records", 2048);
    this.fieldInjectionEnabled =
        agent.getStructured("field_injection/development", empty()).getBoolean("enabled", true);
  }

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.logging = loadString(configFileContents, "otel.javaagent.logging");
    this.extensions = loadString(configFileContents, "otel.javaagent.extensions");
    this.enabled = loadBoolean(configFileContents, "otel.javaagent.enabled", true);
    this.debug = loadBoolean(configFileContents, "otel.javaagent.debug", false);
    this.loggingApplicationLogsBufferMaxRecords =
        loadInt(
            configFileContents, "otel.javaagent.logging.application.logs-buffer-max-records", 2048);
    this.fieldInjectionEnabled =
        loadBoolean(
            configFileContents, "otel.javaagent.experimental.field-injection.enabled", true);
  }

  @Nullable
  public String getLogging() {
    return logging;
  }

  @Nullable
  public String getExtensions() {
    return extensions;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public boolean getDebug() {
    return debug;
  }

  public int getLoggingApplicationLogsBufferMaxRecords() {
    return loggingApplicationLogsBufferMaxRecords;
  }

  public boolean getFieldInjectionEnabled() {
    return fieldInjectionEnabled;
  }

  @Nullable
  private static String loadString(Map<String, String> configFileContents, String propertyName) {
    String value = ConfigPropertiesUtil.getString(propertyName);
    if (value != null) {
      return value;
    }
    return configFileContents.get(propertyName);
  }

  private static boolean loadBoolean(
      Map<String, String> configFileContents, String propertyName, boolean defaultValue) {
    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return ConfigPropertiesUtil.getBoolean(propertyName, configFileValue);
  }

  private static int loadInt(
      Map<String, String> configFileContents, String propertyName, int defaultValue) {
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
    DeclarativeConfigurationFile.logErrorIfAny();
  }
}
