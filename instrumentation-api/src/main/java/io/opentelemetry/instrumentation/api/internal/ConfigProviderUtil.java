/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ConfigProviderUtil {

  static final AbstractBridgedConfigProvider BRIDGED_CONFIG_PROVIDER =
      new AbstractBridgedConfigProvider() {
        @Override
        protected BridgedDeclarativeConfigProperties getProperties(String name) {
          return new BridgedDeclarativeConfigProperties(name, null, ConfigPropertiesUtil::getString);
        }
      };

  /**
   * Returns the ConfigProvider from declarative config if supported, otherwise returns a bridged
   * ConfigProvider that reads from system properties or environment variables.
   */
  public static ConfigProvider getConfigProvider(OpenTelemetry openTelemetry) {
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      return ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider();
    }
    return BRIDGED_CONFIG_PROVIDER;
  }

  private ConfigProviderUtil() {}
}
