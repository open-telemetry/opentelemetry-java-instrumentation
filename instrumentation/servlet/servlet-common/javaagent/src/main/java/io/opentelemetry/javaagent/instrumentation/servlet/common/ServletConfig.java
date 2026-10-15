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
import javax.annotation.Nullable;

class ServletConfig {

  @Nullable private final IncludeExclude requestParameters;
  private final boolean captureExperimentalAttributes;
  private final boolean traceIdRequestAttributeEnabled;
  private final boolean captureRequestBody;
  private final int captureRequestBodyMaxSize;

  static ServletConfig get() {
    return SingletonHolder.INSTANCE;
  }

  ServletConfig(DeclarativeConfigProperties config) {
    requestParameters =
        SelectorConfig.resolve(config, "servlet", "request-parameters", EXPERIMENTAL);
    captureExperimentalAttributes =
        config.getBoolean("experimental_span_attributes/development", false);
    traceIdRequestAttributeEnabled =
        config.get("trace_id_request_attribute/development").getBoolean("enabled", false);

    DeclarativeConfigProperties captureRequestBodyConfig =
        config.get("capture_request_body/development");
    captureRequestBody = captureRequestBodyConfig.getBoolean("enabled", false);

    captureRequestBodyMaxSize = captureRequestBodyConfig.getInt("max_size", 1024);
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

  boolean getCaptureRequestBodyEnabled() {
    return captureRequestBody && captureRequestBodyMaxSize > 0;
  }

  int getCaptureRequestBodyMaxSize() {
    return captureRequestBodyMaxSize;
  }

  private static final class SingletonHolder {
    private static final ServletConfig INSTANCE =
        new ServletConfig(
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "servlet"));
  }
}
