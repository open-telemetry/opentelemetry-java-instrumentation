/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LoggingConfig {

  private static final String STRUCTURED_ATTRIBUTES_PROPERTIES =
      "otel.instrumentation.common.logging.structured-attributes.included or"
          + " otel.instrumentation.common.logging.structured-attributes.excluded";

  /**
   * Resolves the common structured logging attribute selector, retaining the source-specific
   * selector as a fallback until 3.0.
   */
  @Nullable
  public static Predicate<String> resolveStructuredAttributes(
      OpenTelemetry openTelemetry,
      DeclarativeConfigProperties sourceConfig,
      String instrumentationName,
      String selectorName) {
    return resolveStructuredAttributes(
        openTelemetry, sourceConfig, instrumentationName, selectorName, selectorName);
  }

  /**
   * Resolves the common structured logging attribute selector when the deprecated capture setting
   * has a different name from the source-specific selector.
   */
  @Nullable
  public static Predicate<String> resolveStructuredAttributes(
      OpenTelemetry openTelemetry,
      DeclarativeConfigProperties sourceConfig,
      String instrumentationName,
      String selectorName,
      String deprecatedSelectorName) {
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
    if (SemconvStability.v3Preview(openTelemetry)) {
      return value -> true;
    }
    return SelectorConfig.resolveDeprecatedLegacyBoolean(
        sourceConfig,
        instrumentationName,
        selectorName,
        deprecatedSelectorName,
        STRUCTURED_ATTRIBUTES_PROPERTIES);
  }

  private LoggingConfig() {}
}
