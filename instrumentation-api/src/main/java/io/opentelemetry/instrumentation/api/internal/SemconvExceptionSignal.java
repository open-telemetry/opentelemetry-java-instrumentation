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
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvExceptionSignal {

  private static final String CONFIG_PROPERTY = "otel.semconv.exception.signal.preview";

  // only used to ask the LoggerProvider whether it would emit anything
  private static final String INSTRUMENTATION_SCOPE_NAME = "io.opentelemetry.instrumentation.api";

  private static final Logger logger = Logger.getLogger(SemconvExceptionSignal.class.getName());

  private static final boolean emitExceptionAsSpanEvents;
  private static final boolean emitExceptionAsLogs;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.getOrNoop();
    String previewValue = resolvePreviewValue(openTelemetry);

    boolean exceptionAsLogsOptedIn = isExceptionAsLogsOptedIn(previewValue);
    // "logs" replaces span events with log records, "logs/dup" adds log records alongside them
    boolean exceptionAsLogsOnlyOptedIn = "logs".equals(previewValue);

    emitExceptionAsLogs = shouldEmitLogs(exceptionAsLogsOptedIn, previewValue, openTelemetry);
    // span events only stop when log records actually take their place, so that an opt-in that
    // cannot be honored does not drop exceptions
    emitExceptionAsSpanEvents = !exceptionAsLogsOnlyOptedIn || !emitExceptionAsLogs;
  }

  public static boolean emitExceptionAsSpanEvents() {
    return emitExceptionAsSpanEvents;
  }

  public static boolean emitExceptionAsLogs() {
    return emitExceptionAsLogs;
  }

  // visible for testing
  static boolean isExceptionAsLogsOptedIn(@Nullable String value) {
    if ("logs".equals(value) || "logs/dup".equals(value)) {
      return true;
    }

    if (value != null && !value.isEmpty()) {
      logger.warning(
          "Unrecognized value for "
              + CONFIG_PROPERTY
              + ": \""
              + value
              + "\". Expected \"logs\" or \"logs/dup\". Defaulting to span events.");
    }

    return false;
  }

  // visible for testing
  static boolean shouldEmitLogs(
      boolean exceptionAsLogsOptedIn, @Nullable String value, OpenTelemetry openTelemetry) {
    // an exception log record goes nowhere without a LoggerProvider, and an SDK configured without
    // log record processors drops it just the same, so the opt-in cannot be honored in either case
    if (exceptionAsLogsOptedIn && !isLogRecordEmissionEnabled(openTelemetry)) {
      logger.warning(
          CONFIG_PROPERTY
              + " is set to \""
              + value
              + "\", but log record emission is not enabled, so exceptions cannot be emitted as log"
              + " records. Emitting them as span events instead.");
      return false;
    }

    return exceptionAsLogsOptedIn;
  }

  private static boolean isLogRecordEmissionEnabled(OpenTelemetry openTelemetry) {
    // loggerBuilder() rather than get(), as only the former returns the noop Logger that
    // SdkLoggerProvider hands out when it has no log record processors. WARN is the lowest
    // severity exception log records are emitted with, so anything they use is enabled when
    // it is.
    //
    // Note: if the SDK changes the configurations of the loggers it hands out, this check may
    // go stale. However, the SDK appears not to do that at the time of writing this logic.
    return openTelemetry
        .getLogsBridge()
        .loggerBuilder(INSTRUMENTATION_SCOPE_NAME)
        .build()
        .isEnabled(Severity.WARN, Context.root());
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
    // Library instrumentation tests configure this mode using JVM system properties, so a direct
    // system-property fallback is needed.
    return SystemProperty.getString(CONFIG_PROPERTY);
  }

  private static DeclarativeConfigProperties getGeneralInstrumentationConfig(
      OpenTelemetry openTelemetry) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getGeneralInstrumentationConfig()
        : empty();
  }

  private SemconvExceptionSignal() {}
}
