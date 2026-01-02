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

  private static final EarlyInitAgentConfig INSTANCE = create();

  public static EarlyInitAgentConfig get() {
    return INSTANCE;
  }

  /**
   * @deprecated Use {@link #get()} instead.
   */
  @Deprecated
  public static EarlyInitAgentConfig create() {
    return new EarlyInitAgentConfig(ConfigurationFile.getProperties());
  }

  private final Map<String, String> configFileContents;

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.configFileContents = configFileContents;
  }

  @Nullable
  public String getOtelJavaagentLogging() {
    return getString("otel.javaagent.logging");
  }

  @Nullable
  public String getOtelJavaagentExtensions() {
    return getString("otel.javaagent.extensions");
  }

  public boolean isOtelJavaagentDebug() {
    return getBoolean("otel.javaagent.debug", false);
  }

  public boolean isOtelJavaagentEnabled() {
    return getBoolean("otel.javaagent.enabled", true);
  }

  public boolean isOtelJavaagentExperimentalFieldInjectionEnabled() {
    return getBoolean("otel.javaagent.experimental.field-injection.enabled", true);
  }

  public int getOtelJavaagentLoggingApplicationLogsBufferMaxRecords() {
    return getInt("otel.javaagent.logging.application.logs-buffer-max-records", 2048);
  }

  /**
   * @deprecated Use specific property accessors instead.
   */
  @Nullable
  @Deprecated
  public String getString(String propertyName) {
    String value = ConfigPropertiesUtil.getString(propertyName);
    if (value != null) {
      return value;
    }
    return configFileContents.get(propertyName);
  }

  /**
   * @deprecated Use specific property accessors instead.
   */
  @Deprecated
  public boolean getBoolean(String propertyName, boolean defaultValue) {
    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return ConfigPropertiesUtil.getBoolean(propertyName, configFileValue);
  }

  /**
   * @deprecated Use specific property accessors instead.
   */
  @Deprecated
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

  public void logEarlyConfigErrorsIfAny() {
    ConfigurationFile.logErrorIfAny();
  }
}
