/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.MdcAttributeSelectors;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class LogbackConfig {

  private static final Logger logger = Logger.getLogger(LogbackConfig.class.getName());
  private static final Set<String> warnings = ConcurrentHashMap.newKeySet();

  private static final String DEPRECATED_MDC_ATTRIBUTES =
      "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes";
  private static final String MDC_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.included";
  private static final String MDC_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded";

  @Nullable private final Predicate<String> mdcAttributes;

  static LogbackConfig create(OpenTelemetry openTelemetry) {
    return new LogbackConfig(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "logback_appender"));
  }

  LogbackConfig(DeclarativeConfigProperties config) {
    mdcAttributes = getMdcAttributes(config);
  }

  @Nullable
  Predicate<String> getMdcAttributes() {
    return mdcAttributes;
  }

  @Nullable
  private static Predicate<String> getMdcAttributes(DeclarativeConfigProperties config) {
    DeclarativeConfigProperties mdcAttributes = config.get("mdc_attributes/development");
    List<String> included = mdcAttributes.getScalarList("included", String.class);
    List<String> excluded = mdcAttributes.getScalarList("excluded", String.class);
    // An empty selector is equivalent to no selector at all, matching flat configuration where
    // empty property values cannot be distinguished from unset ones.
    Predicate<String> selector =
        MdcAttributeSelectors.create(
            IncludeExclude.builder()
                .setIncluded(included == null ? emptyList() : included)
                .setExcluded(excluded == null ? emptyList() : excluded)
                .build());

    List<String> deprecatedIncluded =
        config.getScalarList("capture_mdc_attributes/development", String.class);
    if (selector != null) {
      if (deprecatedIncluded != null) {
        logWarningOnce(
            "ignored",
            "The "
                + DEPRECATED_MDC_ATTRIBUTES
                + " setting and the equivalent declarative configuration property are deprecated"
                + " and ignored because "
                + MDC_ATTRIBUTES_INCLUDED
                + " or "
                + MDC_ATTRIBUTES_EXCLUDED
                + " is configured. They may be removed in the next minor release.");
      }
      return selector;
    }

    if (deprecatedIncluded == null) {
      return null;
    }
    logWarningOnce(
        "deprecated",
        "The "
            + DEPRECATED_MDC_ATTRIBUTES
            + " setting and the equivalent declarative configuration property are deprecated and"
            + " may be removed in the next minor release. Use "
            + MDC_ATTRIBUTES_INCLUDED
            + " or equivalent declarative configuration instead.");
    // the deprecated setting selects MDC keys by exact equality, except that the single value "*"
    // selects every MDC key
    return MdcAttributeSelectors.createDeprecated(deprecatedIncluded);
  }

  private static void logWarningOnce(String warning, String message) {
    if (warnings.add(warning)) {
      logger.warning(message);
    }
  }
}
