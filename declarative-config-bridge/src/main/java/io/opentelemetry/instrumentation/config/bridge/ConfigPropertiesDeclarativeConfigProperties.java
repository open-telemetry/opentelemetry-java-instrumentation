/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.internal.AbstractBridgedConfigProvider;
import io.opentelemetry.instrumentation.api.internal.BridgedDeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public final class ConfigPropertiesDeclarativeConfigProperties {

  private ConfigPropertiesDeclarativeConfigProperties() {}

  public static ConfigProvider create(ConfigProperties configProperties) {
    return new AbstractBridgedConfigProvider() {
      @Override
      protected BridgedDeclarativeConfigProperties getProperties(String name) {
        return new BridgedDeclarativeConfigProperties(name, null, configProperties::getString);
      }
    };
  }
}

