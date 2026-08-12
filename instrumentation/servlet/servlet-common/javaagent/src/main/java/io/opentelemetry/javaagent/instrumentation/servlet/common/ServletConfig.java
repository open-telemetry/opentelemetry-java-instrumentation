/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

class ServletConfig {

  private static final Logger logger = Logger.getLogger(ServletConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

  private static final String DEPRECATED_REQUEST_PARAMETERS =
      "otel.instrumentation.servlet.experimental.capture-request-parameters";
  private static final String REQUEST_PARAMETERS_INCLUDED =
      "otel.instrumentation.servlet.experimental.request-parameters.included";

  @Nullable private final IncludeExclude requestParameters;
  private final boolean captureExperimentalAttributes;
  private final boolean traceIdRequestAttributeEnabled;

  static ServletConfig get() {
    return SingletonHolder.INSTANCE;
  }

  ServletConfig(DeclarativeConfigProperties config, boolean v3Preview) {
    requestParameters = readRequestParameters(config);
    captureExperimentalAttributes =
        config.getBoolean("experimental_span_attributes/development", false);
    traceIdRequestAttributeEnabled =
        config.get("trace_id_request_attribute/development").getBoolean("enabled", !v3Preview);
  }

  @Nullable
  IncludeExclude getRequestParameters() {
    return requestParameters;
  }

  boolean getCaptureExperimentalAttributes() {
    return captureExperimentalAttributes;
  }

  boolean getTraceIdRequestAttributeEnabled() {
    return traceIdRequestAttributeEnabled;
  }

  @Nullable
  private static IncludeExclude readRequestParameters(DeclarativeConfigProperties config) {
    DeclarativeConfigProperties requestParameters = config.get("request_parameters/development");
    List<String> included = requestParameters.getScalarList("included", String.class);
    List<String> excluded = requestParameters.getScalarList("excluded", String.class);
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

    List<String> deprecatedIncluded =
        config.getScalarList("capture_request_parameters/development", String.class);
    if (deprecatedIncluded == null) {
      return null;
    }

    if (warnedDeprecatedProperties.add(DEPRECATED_REQUEST_PARAMETERS)) {
      logger.warning(
          "The "
              + DEPRECATED_REQUEST_PARAMETERS
              + " setting and the equivalent declarative configuration property"
              + " are deprecated and may be removed in the next minor release. Use "
              + REQUEST_PARAMETERS_INCLUDED
              + " or equivalent declarative configuration instead.");
    }
    return deprecatedIncluded.isEmpty()
        ? null
        : IncludeExclude.builder().setIncluded(deprecatedIncluded).build();
  }

  private static final class SingletonHolder {
    private static final ServletConfig INSTANCE =
        new ServletConfig(
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "servlet"),
            AgentCommonConfig.get().isV3Preview());
  }
}
