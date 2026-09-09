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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LoggingConfig {

  private static final Logger logger = Logger.getLogger(LoggingConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

  /**
   * Resolves the common structured-attribute selector, falling back to the source-specific selector
   * and capture boolean outside v3 preview.
   *
   * <p>A configured common selector takes precedence over source-specific configuration. An empty
   * common selector selects all attributes. In v3 preview, an absent common selector also selects
   * all attributes and source-specific configuration is ignored.
   *
   * <p>Returns {@code null} when the caller should retain its legacy default. This resolver is not
   * intended for MDC or logger-context attributes, which remain opt-in.
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
   * Like {@link #resolveStructuredAttributes(OpenTelemetry, DeclarativeConfigProperties, String,
   * String)}, with a separately named deprecated capture boolean.
   */
  @Nullable
  public static Predicate<String> resolveStructuredAttributes(
      OpenTelemetry openTelemetry,
      DeclarativeConfigProperties sourceConfig,
      String instrumentationName,
      String selectorName,
      String deprecatedSelectorName) {
    DeclarativeConfigProperties logging =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("logging");
    DeclarativeConfigProperties structuredAttributes = logging.get("structured_attributes");
    List<String> included = structuredAttributes.getScalarList("included", String.class);
    List<String> excluded = structuredAttributes.getScalarList("excluded", String.class);
    // The flat configuration bridge always returns a child node, even when it is absent.
    if (included != null
        || excluded != null
        || logging.getPropertyKeys().contains("structured_attributes")) {
      return selector(included, excluded)::matches;
    }
    if (SemconvStability.v3Preview(openTelemetry)) {
      return value -> true;
    }

    // Keep source-specific configuration as a fallback until 3.0.
    DeclarativeConfigProperties sourceSelector =
        sourceConfig.get(selectorName.replace('-', '_') + "/development");
    IncludeExclude selector =
        selector(
            sourceSelector.getScalarList("included", String.class),
            sourceSelector.getScalarList("excluded", String.class));
    if (!selector.isEmpty()) {
      warnDeprecated(
          "otel.instrumentation."
              + instrumentationName
              + ".experimental."
              + selectorName
              + ".included"
              + " or otel.instrumentation."
              + instrumentationName
              + ".experimental."
              + selectorName
              + ".excluded");
      return selector::matches;
    }

    Boolean capture =
        sourceConfig.getBoolean(
            "capture_" + deprecatedSelectorName.replace('-', '_') + "/development");
    if (capture != null) {
      warnDeprecated(
          "otel.instrumentation."
              + instrumentationName
              + ".experimental.capture-"
              + deprecatedSelectorName);
    }
    return Boolean.TRUE.equals(capture) ? value -> true : null;
  }

  private static IncludeExclude selector(
      @Nullable List<String> included, @Nullable List<String> excluded) {
    return IncludeExclude.builder()
        .setIncluded(included == null ? emptyList() : included)
        .setExcluded(excluded == null ? emptyList() : excluded)
        .build();
  }

  private static void warnDeprecated(String property) {
    if (warnedDeprecatedProperties.add(property)) {
      logger.warning(
          "The "
              + property
              + " setting and the equivalent declarative configuration property are deprecated and"
              + " will be removed in 3.0. Use"
              + " otel.instrumentation.common.logging.structured-attributes.included or"
              + " otel.instrumentation.common.logging.structured-attributes.excluded"
              + " or equivalent declarative configuration instead.");
    }
  }

  private LoggingConfig() {}
}
