/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common;

import static io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig.Stability.EXPERIMENTAL;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import javax.annotation.Nullable;

class ServletConfig {

  @Nullable private final IncludeExclude requestParameters;
  private final boolean captureExperimentalAttributes;
  private final boolean traceIdRequestAttributeEnabled;

  static ServletConfig get() {
    return SingletonHolder.INSTANCE;
  }

  ServletConfig(DeclarativeConfigProperties config, boolean v3Preview) {
    requestParameters =
        SelectorConfig.resolve(config, "servlet", "request-parameters", EXPERIMENTAL);
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

  private static final class SingletonHolder {
    private static final ServletConfig INSTANCE =
        new ServletConfig(
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "servlet"),
            AgentCommonConfig.get().isV3Preview());
  }
}
