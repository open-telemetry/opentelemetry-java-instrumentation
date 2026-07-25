/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * A {@link ConfigProvider} implementation backed by {@link ConfigProperties}.
 *
 * @deprecated Use {@link DeclarativeConfigBridge#createInstrumentationConfig(ConfigProperties)}.
 *     This class will be removed in 2.31.0.
 */
@Deprecated // will be removed in 2.31.0
public final class ConfigPropertiesBackedConfigProvider implements ConfigProvider {

  private final ConfigProvider delegate;

  /**
   * @deprecated Use {@link DeclarativeConfigBridge#createInstrumentationConfig(ConfigProperties)}.
   *     This method will be removed in 2.31.0.
   */
  @Deprecated // will be removed in 2.31.0
  public static ConfigProvider create(ConfigProperties configProperties) {
    return new ConfigPropertiesBackedConfigProvider(
        DeclarativeConfigBridge.createInstrumentationConfig(configProperties));
  }

  private ConfigPropertiesBackedConfigProvider(ConfigProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return delegate.getInstrumentationConfig();
  }
}
