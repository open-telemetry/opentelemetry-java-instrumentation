/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common;

import static io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig.Stability.EXPERIMENTAL;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class ServletConfig {

  private static final Logger logger = Logger.getLogger(ServletConfig.class.getName());

  private static final int DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE = 1024;

  @Nullable private final IncludeExclude requestParameters;
  private final boolean captureExperimentalAttributes;
  private final boolean traceIdRequestAttributeEnabled;
  private final boolean captureRequestBody;
  private final int captureRequestBodyMaxSize;

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

    DeclarativeConfigProperties captureRequestBodyConfig =
        config.get("capture_request_body/development");
    captureRequestBody = captureRequestBodyConfig.getBoolean("enabled", false);

    int maxSize =
        captureRequestBodyConfig.getInt("max_size", DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE);
    if (maxSize <= 0) {
      logger.log(
          WARNING,
          "Invalid request body capture max size {0}, using default value {1}",
          new Object[] {maxSize, DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE});
      maxSize = DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE;
    }
    captureRequestBodyMaxSize = maxSize;
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
    return captureRequestBody;
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
