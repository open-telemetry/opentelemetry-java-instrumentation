/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JarAnalyzerConfig {

  private static final Logger logger = Logger.getLogger(JarAnalyzerConfig.class.getName());
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry";
  private static final String LEGACY_INSTRUMENTATION_NAME =
      "io.opentelemetry.runtime-telemetry-java8";

  @Nullable
  public static String getInstrumentationName(
      DeclarativeConfigProperties config, DeclarativeConfigProperties deprecatedConfig) {
    Boolean enabled = config.getBoolean("enabled");
    if (enabled != null) {
      return enabled ? INSTRUMENTATION_NAME : null;
    }

    Boolean deprecatedEnabled = deprecatedConfig.getBoolean("enabled");
    if (deprecatedEnabled != null) {
      logger.warning(
          "otel.instrumentation.runtime-telemetry.package-emitter.enabled is deprecated and will"
              + " be removed in 3.0. Use"
              + " otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled"
              + " instead.");
      return deprecatedEnabled ? LEGACY_INSTRUMENTATION_NAME : null;
    }

    return null;
  }

  private JarAnalyzerConfig() {}
}
