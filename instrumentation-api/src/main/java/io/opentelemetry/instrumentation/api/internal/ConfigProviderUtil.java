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
        protected AbstractBridgedDeclarativeConfigProperties getProperties(String name) {
          return new SystemPropertiesDeclarativeConfigProperties(name, null);
        }
      };
  private static final boolean supportsDeclarativeConfig = supportsDeclarativeConfig();

  private static boolean supportsDeclarativeConfig() {
    try {
      Class.forName("io.opentelemetry.api.incubator.ExtendedOpenTelemetry");
      return true;
    } catch (ClassNotFoundException e) {
      // The incubator module is not available.
      // This only happens in OpenTelemetry API instrumentation tests, where an older version of
      // OpenTelemetry API is used that does not have ExtendedOpenTelemetry.
      // Having the incubator module without ExtendedOpenTelemetry class should still return false
      // for those tests to avoid a ClassNotFoundException.
      return false;
    }
  }

  /** Returns true if the given OpenTelemetry instance supports Declarative Config. */
  public static boolean isDeclarativeConfig(OpenTelemetry openTelemetry) {
    return supportsDeclarativeConfig && openTelemetry instanceof ExtendedOpenTelemetry;
  }

  public static ConfigProvider getConfigProvider(OpenTelemetry openTelemetry) {
    if (isDeclarativeConfig(openTelemetry)) {
      return ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider();
    }
    return BRIDGED_CONFIG_PROVIDER;
  }
}
