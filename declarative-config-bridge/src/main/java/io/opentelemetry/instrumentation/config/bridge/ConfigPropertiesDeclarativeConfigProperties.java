/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;

import io.opentelemetry.instrumentation.api.internal.AbstractSystemPropertiesConfigProvider;
import io.opentelemetry.instrumentation.api.internal.AbstractSystemPropertiesDeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.Nullable;

public class ConfigPropertiesDeclarativeConfigProperties
    extends AbstractSystemPropertiesDeclarativeConfigProperties {

  private final ConfigProperties configProperties;

  private ConfigPropertiesDeclarativeConfigProperties(
      ConfigProperties configProperties,
      String node,
      @Nullable ConfigPropertiesDeclarativeConfigProperties parent) {
    super(node, parent);
    this.configProperties = configProperties;
  }

  public static ConfigProvider create(ConfigProperties configProperties) {
    return new AbstractSystemPropertiesConfigProvider() {
      @Override
      protected AbstractSystemPropertiesDeclarativeConfigProperties getProperties(String name) {
        return new ConfigPropertiesDeclarativeConfigProperties(configProperties, name, null);
      }
    };
  }

  @Nullable
  @Override
  public String getString(String name) {
    return configProperties.getString(name);
  }

  @Override
  protected AbstractSystemPropertiesDeclarativeConfigProperties newChild(String node) {
    return new ConfigPropertiesDeclarativeConfigProperties(configProperties, node, this);
  }
}
