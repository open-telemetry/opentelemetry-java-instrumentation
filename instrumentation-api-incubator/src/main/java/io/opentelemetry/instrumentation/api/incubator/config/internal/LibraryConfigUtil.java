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
public final class LibraryConfigUtil {

  /**
   * Retrieves structured configuration for the given instrumentation name. If no configuration is
   * found, the provided default value is returned.
   *
   * <p>The configuration is retrieved from the provided OpenTelemetry instance. If the instance
   * implements {@link ExtendedOpenTelemetry}, the configuration is fetched from its config
   * provider. Otherwise, the configuration is obtained from system properties or environment
   * variables.
   *
   * <p>This method is only intended to be used for library instrumentation. For agent
   * instrumentation, use {}
   *
   * @param openTelemetry the OpenTelemetry instance
   * @param name the name of the instrumentation
   * @return the structured configuration for the given instrumentation name or the default value
   */
  public static ExtendedDeclarativeConfigProperties getJavaInstrumentationConfig(
      OpenTelemetry openTelemetry, String name) {

    DeclarativeConfigProperties instrumentationConfig =
        openTelemetry instanceof ExtendedOpenTelemetry
            ? ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider().getInstrumentationConfig()
            : SystemPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig();
    if (instrumentationConfig == null) {
      return new ExtendedDeclarativeConfigProperties(empty());
    }
    return new ExtendedDeclarativeConfigProperties(instrumentationConfig).get("java").get(name);
  }

  private LibraryConfigUtil() {}
}
