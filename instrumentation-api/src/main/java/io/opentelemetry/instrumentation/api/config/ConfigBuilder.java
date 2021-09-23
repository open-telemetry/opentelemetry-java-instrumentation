/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ConfigBuilder {

  private final Map<String, String> allProperties = new HashMap<>();

  public ConfigBuilder addProperty(String name, String value) {
    allProperties.put(NamingConvention.DOT.normalize(name), value);
    return this;
  }

  public ConfigBuilder readProperties(Properties properties) {
    for (String name : properties.stringPropertyNames()) {
      allProperties.put(NamingConvention.DOT.normalize(name), properties.getProperty(name));
    }
    return this;
  }

  public ConfigBuilder readProperties(Map<String, String> properties) {
    return fromConfigMap(properties, NamingConvention.DOT);
  }

  /** Sets the configuration values from environment variables. */
  public ConfigBuilder readEnvironmentVariables() {
    return fromConfigMap(System.getenv(), NamingConvention.ENV_VAR);
  }

  /** Sets the configuration values from system properties. */
  public ConfigBuilder readSystemProperties() {
    return readProperties(System.getProperties());
  }

  private ConfigBuilder fromConfigMap(
      Map<String, String> configMap, NamingConvention namingConvention) {
    for (Map.Entry<String, String> entry : configMap.entrySet()) {
      allProperties.put(namingConvention.normalize(entry.getKey()), entry.getValue());
    }
    return this;
  }

  public Config build() {
    return Config.create(Collections.unmodifiableMap(new HashMap<>(allProperties)));
  }
}
