/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
