/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("deprecation")
public final class SemconvExceptionSignal {

  private static final String CONFIG_PROPERTY = "otel.semconv.exception.signal.preview";

  private static final Logger logger = Logger.getLogger(SemconvExceptionSignal.class.getName());

  private static final boolean emitExceptionAsSpanEvents;
  private static final boolean emitExceptionAsLogs;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.getOrNoop();
    String previewValue = resolvePreviewValue(openTelemetry);

    emitExceptionAsSpanEvents = shouldEmitSpanEvents(previewValue);
    emitExceptionAsLogs = shouldEmitLogs(previewValue);
  }

  public static boolean emitExceptionAsSpanEvents() {
    return emitExceptionAsSpanEvents;
  }

  public static boolean emitExceptionAsLogs() {
    return emitExceptionAsLogs;
  }

  private static boolean shouldEmitSpanEvents(@Nullable String value) {
    return !"logs".equals(value);
  }

  private static boolean shouldEmitLogs(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    if ("logs".equals(value) || "logs/dup".equals(value)) {
      return true;
    }
    logger.warning(
        "Unrecognized value for "
            + CONFIG_PROPERTY
            + ": \""
            + value
            + "\". Expected \"logs\" or \"logs/dup\". Defaulting to span events.");
    return false;
  }

  @Nullable
  private static String resolvePreviewValue(OpenTelemetry openTelemetry) {
    // Try declarative config via GlobalOpenTelemetry first
    String value =
        getGeneralInstrumentationConfig(openTelemetry)
            .get("semconv_exception")
            .get("signal")
            .getString("preview");
    if (value != null) {
      return value;
    }
    return ConfigPropertiesUtil.getString(CONFIG_PROPERTY);
  }

  private static DeclarativeConfigProperties getGeneralInstrumentationConfig(
      OpenTelemetry openTelemetry) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getGeneralInstrumentationConfig()
        : empty();
  }

  private SemconvExceptionSignal() {}
}
