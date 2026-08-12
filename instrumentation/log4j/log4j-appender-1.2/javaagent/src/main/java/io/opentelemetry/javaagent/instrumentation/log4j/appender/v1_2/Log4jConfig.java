/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

class Log4jConfig {

  private static final Logger logger = Logger.getLogger(Log4jConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

  private static final String DEPRECATED_CAPTURE_MDC_ATTRIBUTES =
      "otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes";
  private static final String MDC_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.log4j-appender.experimental.mdc-attributes.included";
  private static final String MDC_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded";

  @Nullable private final Predicate<String> contextDataAttributes;

  static Log4jConfig create(OpenTelemetry openTelemetry) {
    return new Log4jConfig(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "log4j_appender"));
  }

  Log4jConfig(DeclarativeConfigProperties config) {
    contextDataAttributes = getContextDataAttributes(config);
  }

  @Nullable
  Predicate<String> getContextDataAttributes() {
    return contextDataAttributes;
  }

  @Nullable
  private static Predicate<String> getContextDataAttributes(DeclarativeConfigProperties config) {
    DeclarativeConfigProperties mdcAttributes = config.get("mdc_attributes/development");
    List<String> included = mdcAttributes.getScalarList("included", String.class);
    List<String> excluded = mdcAttributes.getScalarList("excluded", String.class);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();

    // Deprecated include-only alias. Its entries are matched literally, except that a list
    // containing only "*" captures every MDC attribute.
    List<String> deprecatedIncluded =
        config.getScalarList("capture_mdc_attributes/development", String.class);
    if (!selector.isEmpty()) {
      if (deprecatedIncluded != null) {
        logWarningOnce(
            "precedence",
            "The "
                + DEPRECATED_CAPTURE_MDC_ATTRIBUTES
                + " setting and the equivalent declarative configuration property are deprecated"
                + " and ignored because "
                + MDC_ATTRIBUTES_INCLUDED
                + " or "
                + MDC_ATTRIBUTES_EXCLUDED
                + " is configured. They may be removed in the next minor release.");
      }
      return selector::matches;
    }

    if (deprecatedIncluded == null) {
      return null;
    }
    logWarningOnce(
        "deprecation",
        "The "
            + DEPRECATED_CAPTURE_MDC_ATTRIBUTES
            + " setting and the equivalent declarative configuration property are deprecated and"
            + " may be removed in the next minor release. Use "
            + MDC_ATTRIBUTES_INCLUDED
            + " or equivalent declarative configuration instead.");
    if (deprecatedIncluded.isEmpty()) {
      return null;
    }
    if (deprecatedIncluded.size() == 1 && deprecatedIncluded.get(0).equals("*")) {
      return key -> true;
    }
    Set<String> exactKeys = new HashSet<>(deprecatedIncluded);
    return exactKeys::contains;
  }

  private static void logWarningOnce(String warning, String message) {
    if (warnedDeprecatedProperties.add(warning)) {
      logger.warning(message);
    }
  }
}
