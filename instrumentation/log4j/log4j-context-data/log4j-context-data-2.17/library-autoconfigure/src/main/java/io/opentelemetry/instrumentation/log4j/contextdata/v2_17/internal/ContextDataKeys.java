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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextDataKeys {
  private static final Logger logger = Logger.getLogger(ContextDataKeys.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

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
            "trace_id",
            "otel.instrumentation.common.logging.trace-id-key",
            "otel.instrumentation.common.logging.trace-id",
            LoggingContextConstants.TRACE_ID);
    String spanIdKey =
        getConfig(
            logging,
            "span_id_key",
            "span_id",
            "otel.instrumentation.common.logging.span-id-key",
            "otel.instrumentation.common.logging.span-id",
            LoggingContextConstants.SPAN_ID);
    String traceFlagsKey =
        getConfig(
            logging,
            "trace_flags_key",
            "trace_flags",
            "otel.instrumentation.common.logging.trace-flags-key",
            "otel.instrumentation.common.logging.trace-flags",
            LoggingContextConstants.TRACE_FLAGS);
    return new ContextDataKeys(traceIdKey, spanIdKey, traceFlagsKey);
  }

  @SuppressWarnings("deprecation") // using deprecated ConfigPropertiesUtil
  private static String getConfig(
      DeclarativeConfigProperties config,
      String newDeclarativeKey,
      String oldDeclarativeKey,
      String newProperty,
      String oldProperty,
      String defaultValue) {
    String value = config.getString(newDeclarativeKey);
    if (value != null) {
      return value;
    }
    value = config.getString(oldDeclarativeKey);
    if (value != null) {
      logDeprecationWarning(
          oldProperty,
          "The "
              + oldProperty
              + " setting and the equivalent declarative configuration property"
              + " are deprecated and will be removed in 3.0. Use "
              + newProperty
              + " or equivalent declarative configuration instead.");
      return value;
    }
    value = ConfigPropertiesUtil.getString(newProperty);
    if (value != null) {
      return value;
    }
    value = ConfigPropertiesUtil.getString(oldProperty);
    if (value != null) {
      logDeprecationWarning(
          oldProperty,
          "The '"
              + oldProperty
              + "' system property is deprecated and will be removed in 3.0. Use '"
              + newProperty
              + "' instead.");
      return value;
    }
    return defaultValue;
  }

  private static void logDeprecationWarning(String deprecatedProperty, String message) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(message);
    }
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
