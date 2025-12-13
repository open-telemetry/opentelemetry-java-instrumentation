/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.internal.BridgedConfigProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public final class ConfigPropertiesDeclarativeConfigProperties {

  private ConfigPropertiesDeclarativeConfigProperties() {}

  public static ConfigProvider create(ConfigProperties configProperties) {
    return new BridgedConfigProvider(configProperties::getString);
  }
}
