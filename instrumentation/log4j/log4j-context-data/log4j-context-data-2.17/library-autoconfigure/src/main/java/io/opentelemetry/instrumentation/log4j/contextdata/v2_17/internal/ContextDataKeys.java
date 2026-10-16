/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.internal.SystemProperty;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextDataKeys {
  private final String traceIdKey;
  private final String spanIdKey;
  private final String traceFlagsKey;

  public static ContextDataKeys create(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties logging =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("logging");
    String traceIdKey =
        getConfig(
            logging,
            "trace_id_key",
            "otel.instrumentation.common.logging.trace-id-key",
            LoggingContextConstants.TRACE_ID);
    String spanIdKey =
        getConfig(
            logging,
            "span_id_key",
            "otel.instrumentation.common.logging.span-id-key",
            LoggingContextConstants.SPAN_ID);
    String traceFlagsKey =
        getConfig(
            logging,
            "trace_flags_key",
            "otel.instrumentation.common.logging.trace-flags-key",
            LoggingContextConstants.TRACE_FLAGS);
    return new ContextDataKeys(traceIdKey, spanIdKey, traceFlagsKey);
  }

  private static String getConfig(
      DeclarativeConfigProperties config,
      String declarativeKey,
      String property,
      String defaultValue) {
    String value = config.getString(declarativeKey);
    if (value != null) {
      return value;
    }
    // The context data provider is loaded through the Log4j SPI and has no programmatic
    // configuration API. Declarative instrumentation configuration is not stable yet, so a
    // system-property fallback is still needed.
    value = SystemProperty.getString(property);
    if (value != null) {
      return value;
    }
    return defaultValue;
  }

  private ContextDataKeys(String traceIdKey, String spanIdKey, String traceFlagsKey) {
    this.traceIdKey = traceIdKey;
    this.spanIdKey = spanIdKey;
    this.traceFlagsKey = traceFlagsKey;
  }

  public String getTraceIdKey() {
    return traceIdKey;
  }

  public String getSpanIdKey() {
    return spanIdKey;
  }

  public String getTraceFlagsKey() {
    return traceFlagsKey;
  }
}
