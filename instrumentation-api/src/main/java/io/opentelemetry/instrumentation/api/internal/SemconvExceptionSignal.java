/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvExceptionSignal {

  private static final Logger logger = Logger.getLogger(SemconvExceptionSignal.class.getName());

  private static final boolean emitExceptionAsSpanEvents;
  private static final boolean emitExceptionAsLogs;

  static {
    boolean spanEvents = true;
    boolean logs = false;

    String value = System.getProperty("otel.semconv.exception.signal.opt-in");
    if (value == null) {
      value = System.getenv("OTEL_SEMCONV_EXCEPTION_SIGNAL_OPT_IN");
    }
    if (value != null) {
      if (value.equals("logs")) {
        spanEvents = false;
        logs = true;
      } else if (value.equals("logs/dup")) {
        spanEvents = true;
        logs = true;
      } else if (!value.isEmpty()) {
        logger.warning(
            "Unrecognized value for otel.semconv.exception.signal.opt-in: \""
                + value
                + "\". Expected \"logs\" or \"logs/dup\". Defaulting to span events.");
      }
    }

    emitExceptionAsSpanEvents = spanEvents;
    emitExceptionAsLogs = logs;
  }

  public static boolean emitExceptionAsSpanEvents() {
    return emitExceptionAsSpanEvents;
  }

  public static boolean emitExceptionAsLogs() {
    return emitExceptionAsLogs;
  }

  private SemconvExceptionSignal() {}
}
