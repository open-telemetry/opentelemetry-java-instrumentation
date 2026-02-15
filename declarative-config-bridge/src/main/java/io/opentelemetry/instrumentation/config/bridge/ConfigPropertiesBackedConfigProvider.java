/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ConfigProvider} implementation backed by {@link ConfigProperties}.
 *
 * <p>This allows instrumentations to always use {@code ExtendedOpenTelemetry.getConfigProvider()}
 * regardless of whether the user started with system properties or YAML.
 */
public final class ConfigPropertiesBackedConfigProvider implements ConfigProvider {

  private final DeclarativeConfigProperties instrumentationConfig;

  public static ConfigProvider create(ConfigProperties configProperties) {
    return new ConfigPropertiesBackedConfigProvider(
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            configProperties));
  }

  public static Builder builder() {
    return new Builder();
  }

  private ConfigPropertiesBackedConfigProvider(DeclarativeConfigProperties instrumentationConfig) {
    this.instrumentationConfig = instrumentationConfig;
  }

  public static final class Builder {
    private final Map<String, String> mappings = new HashMap<>();

    @com.google.errorprone.annotations.CanIgnoreReturnValue
    public Builder addMapping(String declarativeProperty, String configProperty) {
      mappings.put(declarativeProperty, configProperty);
      return this;
    }

    public ConfigProvider build(ConfigProperties configProperties) {
      return new ConfigPropertiesBackedConfigProvider(
          ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
              configProperties, mappings));
    }
  }

  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }
}
