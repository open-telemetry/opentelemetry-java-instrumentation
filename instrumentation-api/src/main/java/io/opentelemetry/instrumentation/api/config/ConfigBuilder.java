/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;

/** A builder of a {@link Config}. */
public final class ConfigBuilder {

  private final Map<String, String> allProperties = new HashMap<>();

  /** Constructs a new {@link ConfigBuilder}. */
  ConfigBuilder() {}

  /** Adds a single property named to the config. */
  public ConfigBuilder addProperty(String name, @Nullable String value) {
    if (value != null) {
      allProperties.put(NamingConvention.DOT.normalize(name), value);
    }
    return this;
  }

  /** Adds all properties from the passed {@code properties} to the config. */
  public ConfigBuilder addProperties(Properties properties) {
    for (String name : properties.stringPropertyNames()) {
      allProperties.put(NamingConvention.DOT.normalize(name), properties.getProperty(name));
    }
    return this;
  }

  /** Adds all properties from the passed {@code properties} to the config. */
  public ConfigBuilder addProperties(Map<String, String> properties) {
    return fromConfigMap(properties, NamingConvention.DOT);
  }

  /**
   * Adds environment variables, converted to Java property naming convention, to the config.
   *
   * <p>Environment variable names are lowercased, and the underscores are replaced by dots.
   */
  public ConfigBuilder addEnvironmentVariables() {
    return fromConfigMap(System.getenv(), NamingConvention.ENV_VAR);
  }

  /** Adds system properties to the config. */
  public ConfigBuilder addSystemProperties() {
    return addProperties(System.getProperties());
  }

  private ConfigBuilder fromConfigMap(
      Map<String, String> configMap, NamingConvention namingConvention) {
    for (Map.Entry<String, String> entry : configMap.entrySet()) {
      allProperties.put(namingConvention.normalize(entry.getKey()), entry.getValue());
    }
    return this;
  }

  /** Returns a new {@link Config} with properties from this {@linkplain ConfigBuilder builder}. */
  public Config build() {
    return Config.create(Collections.unmodifiableMap(new HashMap<>(allProperties)));
  }
}
