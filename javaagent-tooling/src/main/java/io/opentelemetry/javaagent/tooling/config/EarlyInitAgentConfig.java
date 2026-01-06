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

  @Nullable private final String otelJavaagentLogging;
  @Nullable private final String otelJavaagentExtensions;
  private final boolean otelJavaagentEnabled;
  private final boolean otelJavaagentDebug;
  private final int otelJavaagentLoggingApplicationLogsBufferMaxRecords;
  private final boolean otelJavaagentExperimentalFieldInjectionEnabled;

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

    this.otelJavaagentLogging = agent.getString("logging");
    this.otelJavaagentExtensions = agent.getString("extensions");
    this.otelJavaagentEnabled = agent.getBoolean("enabled", true);
    this.otelJavaagentDebug = agent.getBoolean("debug", false);
    this.otelJavaagentLoggingApplicationLogsBufferMaxRecords =
        agent
            .getStructured("logging", empty())
            .getStructured("application", empty())
            .getInt("logs_buffer_max_records", 2048);
    this.otelJavaagentExperimentalFieldInjectionEnabled =
        agent
            .getStructured("experimental", empty())
            .getStructured("field_injection", empty())
            .getBoolean("enabled", true);
  }

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.otelJavaagentLogging = loadString(configFileContents, "otel.javaagent.logging");
    this.otelJavaagentExtensions = loadString(configFileContents, "otel.javaagent.extensions");
    this.otelJavaagentEnabled = loadBoolean(configFileContents, "otel.javaagent.enabled", true);
    this.otelJavaagentDebug = loadBoolean(configFileContents, "otel.javaagent.debug", false);
    this.otelJavaagentLoggingApplicationLogsBufferMaxRecords =
        loadInt(
            configFileContents, "otel.javaagent.logging.application.logs-buffer-max-records", 2048);
    this.otelJavaagentExperimentalFieldInjectionEnabled =
        loadBoolean(
            configFileContents, "otel.javaagent.experimental.field-injection.enabled", true);
  }

  @Nullable
  public String getOtelJavaagentLogging() {
    return otelJavaagentLogging;
  }

  @Nullable
  public String getOtelJavaagentExtensions() {
    return otelJavaagentExtensions;
  }

  public boolean getOtelJavaagentEnabled() {
    return otelJavaagentEnabled;
  }

  public boolean getOtelJavaagentDebug() {
    return otelJavaagentDebug;
  }

  public int getOtelJavaagentLoggingApplicationLogsBufferMaxRecords() {
    return otelJavaagentLoggingApplicationLogsBufferMaxRecords;
  }

  public boolean getOtelJavaagentExperimentalFieldInjectionEnabled() {
    return otelJavaagentExperimentalFieldInjectionEnabled;
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
