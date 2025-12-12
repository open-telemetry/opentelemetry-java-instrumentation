/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.internal.AbstractSystemPropertiesDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.Nullable;

public class ConfigPropertiesDeclarativeConfigProperties
    extends AbstractSystemPropertiesDeclarativeConfigProperties {

  private final ConfigProperties configProperties;

  public ConfigPropertiesDeclarativeConfigProperties(
      ConfigProperties configProperties,
      String node,
      @Nullable ConfigPropertiesDeclarativeConfigProperties parent) {
    super(node, parent);
    this.configProperties = configProperties;
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
