/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import io.opentelemetry.instrumentation.api.config.Config;

/**
 * This class encapsulates query normalization property naming convention. Query normalization is
 * always enabled by default, you have to manually disable it.
 */
public final class QueryNormalizationConfig {

  public static boolean isQueryNormalizationEnabled(String... instrumentationNames) {
    for (String instrumentationName : instrumentationNames) {
      if (!Config.get().getBooleanProperty(propertyName(instrumentationName), true)) {
        return false;
      }
    }
    return true;
  }

  private static String propertyName(String instrumentationName) {
    return "otel.instrumentation." + instrumentationName + ".query.normalizer.enabled";
  }

  private QueryNormalizationConfig() {}
}
