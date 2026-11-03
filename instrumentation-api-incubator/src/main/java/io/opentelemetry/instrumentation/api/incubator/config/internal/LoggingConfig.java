/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LoggingConfig {

  /**
   * Resolves the common structured logging attribute selector, capturing all attributes when it is
   * absent or empty.
   */
  public static Predicate<String> resolveStructuredAttributes(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties loggingConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("logging");
    DeclarativeConfigProperties selectorConfig = loggingConfig.get("structured_attributes");
    List<String> included = selectorConfig.getScalarList("included", String.class);
    List<String> excluded = selectorConfig.getScalarList("excluded", String.class);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    if (!selector.isEmpty()) {
      return selector::matches;
    }
    return value -> true;
  }

  private LoggingConfig() {}
}
