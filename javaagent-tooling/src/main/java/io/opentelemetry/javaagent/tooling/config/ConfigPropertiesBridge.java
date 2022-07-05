/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

public final class ConfigPropertiesBridge extends InstrumentationConfig {

  private final ConfigProperties configProperties;

  public ConfigPropertiesBridge(ConfigProperties configProperties) {
    this.configProperties = configProperties;
  }

  @Nullable
  @Override
  public String getString(String name) {
    try {
      return configProperties.getString(normalize(name));
    } catch (ConfigurationException ignored) {
      return null;
    }
  }

  @Override
  public String getString(String name, String defaultValue) {
    try {
      return configProperties.getString(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public boolean getBoolean(String name, boolean defaultValue) {
    try {
      return configProperties.getBoolean(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public int getInt(String name, int defaultValue) {
    try {
      return configProperties.getInt(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public long getLong(String name, long defaultValue) {
    try {
      return configProperties.getLong(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public double getDouble(String name, double defaultValue) {
    try {
      return configProperties.getDouble(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public Duration getDuration(String name, Duration defaultValue) {
    try {
      return configProperties.getDuration(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public List<String> getList(String name, List<String> defaultValue) {
    try {
      return configProperties.getList(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  @Override
  public Map<String, String> getMap(String name, Map<String, String> defaultValue) {
    try {
      return configProperties.getMap(normalize(name), defaultValue);
    } catch (ConfigurationException ignored) {
      return defaultValue;
    }
  }

  // TODO: remove after https://github.com/open-telemetry/opentelemetry-java/issues/4562 is fixed
  private static String normalize(String key) {
    return key.toLowerCase(Locale.ROOT).replace('-', '.');
  }
}
