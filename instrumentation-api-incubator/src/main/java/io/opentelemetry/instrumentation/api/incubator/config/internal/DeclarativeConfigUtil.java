/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DeclarativeConfigUtil {

  private DeclarativeConfigUtil() {}

  public static ExtendedDeclarativeConfigProperties getInstrumentationConfig(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return getConfig(openTelemetry).get("java").get(instrumentationName);
  }

  public static ExtendedDeclarativeConfigProperties getGeneralInstrumentationConfig(
      OpenTelemetry openTelemetry) {
    return getConfig(openTelemetry).get("general");
  }

  private static ExtendedDeclarativeConfigProperties getConfig(OpenTelemetry openTelemetry) {
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      DeclarativeConfigProperties instrumentationConfig =
          extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
      if (instrumentationConfig != null) {
        return new ExtendedDeclarativeConfigProperties(instrumentationConfig);
      }
    }
    return new ExtendedDeclarativeConfigProperties(empty());
  }
}
