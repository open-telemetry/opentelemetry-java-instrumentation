/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.api.config.Config;

/**
 * This class encapsulates query normalization property naming convention. Query normalization is
 * always enabled by default, you have to manually disable it.
 */
public final class QueryNormalizationConfig {

  public static boolean isQueryNormalizationEnabled(String... instrumentationNames) {
    return Config.get()
        .getInstrumentationBooleanProperty(
            asList(instrumentationNames), "query.normalizer.enabled", true);
  }

  private QueryNormalizationConfig() {}
}
