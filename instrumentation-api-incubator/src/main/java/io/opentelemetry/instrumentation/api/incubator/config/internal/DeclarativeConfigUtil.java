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

  public static ExtendedDeclarativeConfigProperties get(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties javaConfig = empty();
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      DeclarativeConfigProperties instrumentationConfig =
          extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
      if (instrumentationConfig != null) {
        javaConfig = instrumentationConfig.getStructured("java", empty());
      }
    }
    return new ExtendedDeclarativeConfigProperties(javaConfig);
  }
}
