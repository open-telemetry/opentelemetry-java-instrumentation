/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.Nullable;

/**
 * A {@link ConfigProvider} implementation backed by {@link ConfigProperties}.
 *
 * <p>This is used when the user configured with system properties (not YAML), so we create a
 * ConfigProvider that provides access to the configuration through the declarative config API.
 *
 * <p>This allows instrumentations to always use {@code ExtendedOpenTelemetry.getConfigProvider()}
 * regardless of whether the user started with system properties or YAML.
 */
public final class ConfigPropertiesBackedConfigProvider implements ConfigProvider {

  private final DeclarativeConfigProperties instrumentationConfig;

  /**
   * Creates a ConfigProvider backed by the given ConfigProperties.
   *
   * @param configProperties the ConfigProperties from SDK autoconfiguration
   * @return a new ConfigProvider instance
   */
  public static ConfigProvider create(ConfigProperties configProperties) {
    return new ConfigPropertiesBackedConfigProvider(configProperties);
  }

  private ConfigPropertiesBackedConfigProvider(ConfigProperties configProperties) {
    // The instrumentation config is rooted at "otel.instrumentation."
    this.instrumentationConfig =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            configProperties);
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }
}
