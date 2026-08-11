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
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class LogbackConfig {

  private static final Logger logger = Logger.getLogger(LogbackConfig.class.getName());
  private static final AtomicBoolean warnedDeprecatedProperty = new AtomicBoolean();

  private static final String DEPRECATED_MDC_ATTRIBUTES =
      "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes";
  private static final String MDC_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.included";

  @Nullable private final IncludeExclude mdcAttributes;

  static LogbackConfig create(OpenTelemetry openTelemetry) {
    return new LogbackConfig(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "logback_appender"),
        SemconvStability.v3Preview(openTelemetry));
  }

  LogbackConfig(DeclarativeConfigProperties config, boolean v3Preview) {
    mdcAttributes = getMdcAttributes(config, v3Preview);
  }

  @Nullable
  IncludeExclude getMdcAttributes() {
    return mdcAttributes;
  }

  @Nullable
  private static IncludeExclude getMdcAttributes(
      DeclarativeConfigProperties config, boolean v3Preview) {
    DeclarativeConfigProperties mdcAttributes = config.get("mdc_attributes/development");
    List<String> included = mdcAttributes.getScalarList("included", String.class);
    List<String> excluded = mdcAttributes.getScalarList("excluded", String.class);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    // An empty selector is equivalent to no selector at all, matching flat configuration where
    // empty property values cannot be distinguished from unset ones.
    if (!selector.isEmpty()) {
      return selector;
    }

    if (v3Preview) {
      return null;
    }

    List<String> deprecatedIncluded =
        config.getScalarList("capture_mdc_attributes/development", String.class);
    if (deprecatedIncluded == null) {
      return null;
    }

    if (warnedDeprecatedProperty.compareAndSet(false, true)) {
      logger.warning(
          "The "
              + DEPRECATED_MDC_ATTRIBUTES
              + " setting and the equivalent declarative configuration property are deprecated"
              + " and will be removed in 3.0. Use "
              + MDC_ATTRIBUTES_INCLUDED
              + " or equivalent declarative configuration instead.");
    }
    return deprecatedIncluded.isEmpty()
        ? null
        : IncludeExclude.builder().setIncluded(deprecatedIncluded).build();
  }
}
