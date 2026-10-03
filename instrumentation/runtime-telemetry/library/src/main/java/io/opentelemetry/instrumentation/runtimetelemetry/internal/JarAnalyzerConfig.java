/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JarAnalyzerConfig {

  private static final int DEFAULT_JARS_PER_SECOND = 10;
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry";

  @Nullable
  public static String getInstrumentationName(DeclarativeConfigProperties config) {
    return config.getBoolean("enabled", false) ? INSTRUMENTATION_NAME : null;
  }

  public static int getJarsPerSecond(DeclarativeConfigProperties config) {
    int jarsPerSecond = config.getInt("jars_per_second", -1);
    if (jarsPerSecond >= 0) {
      return jarsPerSecond;
    }

    return DEFAULT_JARS_PER_SECOND;
  }

  private JarAnalyzerConfig() {}
}
