/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextDataKeys {

  private final String traceIdKey;
  private final String spanIdKey;
  private final String traceFlags;

  private ContextDataKeys(String traceIdKey, String spanIdKey, String traceFlags) {
    this.traceIdKey = traceIdKey;
    this.spanIdKey = spanIdKey;
    this.traceFlags = traceFlags;
  }

  public static ContextDataKeys create(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties logging =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("logging");
    return new ContextDataKeys(
        logging.getString(
            "trace_id",
            ConfigPropertiesUtil.getString(
                "otel.instrumentation.common.logging.trace-id", LoggingContextConstants.TRACE_ID)),
        logging.getString(
            "span_id",
            ConfigPropertiesUtil.getString(
                "otel.instrumentation.common.logging.span-id", LoggingContextConstants.SPAN_ID)),
        logging.getString(
            "trace_flags",
            ConfigPropertiesUtil.getString(
                "otel.instrumentation.common.logging.trace-flags",
                LoggingContextConstants.TRACE_FLAGS)));
  }

  public String getTraceIdKey() {
    return traceIdKey;
  }

  public String getSpanIdKey() {
    return spanIdKey;
  }

  public String getTraceFlags() {
    return traceFlags;
  }
}
