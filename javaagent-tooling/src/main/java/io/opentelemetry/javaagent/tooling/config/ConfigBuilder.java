/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.instrumentation.api.config.Config.normalizePropertyName;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ConfigBuilder
    extends io.opentelemetry.sdk.common.export.ConfigBuilder<ConfigBuilder> {
  private final Map<String, String> allProperties = new HashMap<>();

  @Override
  public ConfigBuilder readProperties(Properties properties) {
    return this.fromConfigMap(normalizedProperties(properties), NamingConvention.DOT);
  }

  private static Map<String, String> normalizedProperties(Properties properties) {
    Map<String, String> configMap = new HashMap<>(properties.size());
    properties.forEach(
        (propertyName, propertyValue) ->
            configMap.put(normalizePropertyName((String) propertyName), (String) propertyValue));
    return configMap;
  }

  ConfigBuilder readPropertiesFromAllSources(
      Properties spiConfiguration, Properties configurationFile) {
    // ordering from least to most important
    return readProperties(spiConfiguration)
        .readProperties(configurationFile)
        .readEnvironmentVariables()
        .readSystemProperties();
  }

  @Override
  protected ConfigBuilder fromConfigMap(
      Map<String, String> configMap, NamingConvention namingConvention) {
    configMap = namingConvention.normalize(configMap);
    allProperties.putAll(configMap);
    return this;
  }

  Config build() {
    return Config.create(allProperties);
  }
}
