/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DeclarativeConfigUtil {

  // this is a temporary convenience until getConfigProvider is available on OpenTelemetry
  public static DeclarativeConfigProperties getStructured(
      OpenTelemetry openTelemetry, String name, DeclarativeConfigProperties defaultValue) {

    if (!(openTelemetry instanceof ExtendedOpenTelemetry)) {
      return defaultValue;
    }
    ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
    DeclarativeConfigProperties instrumentationConfig =
        extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
    if (instrumentationConfig == null) {
      return defaultValue;
    }
    return instrumentationConfig.getStructured(name, defaultValue);
  }

  private DeclarativeConfigUtil() {}
}
