/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common;

import static io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig.Stability.EXPERIMENTAL;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig.*;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig;
import javax.annotation.Nullable;

public class ServletConfig {

  @Nullable private final IncludeExclude requestParameters;
  private final boolean captureExperimentalAttributes;
  private final boolean traceIdRequestAttributeEnabled;

  private final BodyCaptureConfig bodyCaptureConfig;

  public static ServletConfig get() {
    return SingletonHolder.INSTANCE;
  }

  ServletConfig(DeclarativeConfigProperties config) {
    requestParameters =
        SelectorConfig.resolve(config, "servlet", "request-parameters", EXPERIMENTAL);
    captureExperimentalAttributes =
        config.getBoolean("experimental_span_attributes/development", false);
    traceIdRequestAttributeEnabled =
        config.get("trace_id_request_attribute/development").getBoolean("enabled", false);

    DeclarativeConfigProperties captureRequestBody = config.get("capture_request_body/development");
    bodyCaptureConfig =
        createDefault()
            .setEnabled(captureRequestBody.getBoolean("enabled", false))
            .setMaxSize(
                captureRequestBody.getInt("max_size", DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE));
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

  public boolean getCaptureRequestBodyEnabled() {
    return bodyCaptureConfig.isEnabled();
  }

  public BodyCaptureConfig getRequestBodyCaptureConfig() {
    return bodyCaptureConfig;
  }

  private static final class SingletonHolder {
    private static final ServletConfig INSTANCE =
        new ServletConfig(
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "servlet"));
  }
}
