/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.config.ConfigParsingException;
import io.opentelemetry.sdk.autoconfigure.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.ConfigurationException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ConfigPropertiesAdapter implements ConfigProperties {
  private final Config config;

  public ConfigPropertiesAdapter(Config config) {
    this.config = config;
  }

  @Nullable
  @Override
  public String getString(String name) {
    return config.getString(name);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return config.getBoolean(name);
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    try {
      return config.getInt(name);
    } catch (ConfigParsingException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    try {
      return config.getLong(name);
    } catch (ConfigParsingException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    try {
      return config.getDouble(name);
    } catch (ConfigParsingException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    try {
      return config.getDuration(name);
    } catch (ConfigParsingException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  @Override
  public List<String> getCommaSeparatedValues(String name) {
    return config.getList(name);
  }

  @Override
  public Map<String, String> getCommaSeparatedMap(String name) {
    try {
      return config.getMap(name);
    } catch (ConfigParsingException e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }
}
