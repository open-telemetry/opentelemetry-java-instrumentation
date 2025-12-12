/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.internal.AbstractBridgedConfigProvider;
import io.opentelemetry.instrumentation.api.internal.AbstractBridgedDeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.Nullable;

public class ConfigPropertiesDeclarativeConfigProperties
    extends AbstractBridgedDeclarativeConfigProperties {

  private final ConfigProperties configProperties;

  private ConfigPropertiesDeclarativeConfigProperties(
      ConfigProperties configProperties,
      String node,
      @Nullable ConfigPropertiesDeclarativeConfigProperties parent) {
    super(node, parent);
    this.configProperties = configProperties;
  }

  public static ConfigProvider create(ConfigProperties configProperties) {
    return new AbstractBridgedConfigProvider() {
      @Override
      protected AbstractBridgedDeclarativeConfigProperties getProperties(String name) {
        return new ConfigPropertiesDeclarativeConfigProperties(configProperties, name, null);
      }
    };
  }

  @Nullable
  @Override
  public String getStringValue(String systemPropertyKey) {
    return configProperties.getString(systemPropertyKey);
  }

  @Override
  protected AbstractBridgedDeclarativeConfigProperties newChild(String node) {
    return new ConfigPropertiesDeclarativeConfigProperties(configProperties, node, this);
  }
}
