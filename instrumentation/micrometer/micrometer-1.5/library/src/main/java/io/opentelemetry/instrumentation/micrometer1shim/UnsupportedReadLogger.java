/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import java.util.logging.Level;
import java.util.logging.Logger;

final class UnsupportedReadLogger {

  static {
    Logger logger = Logger.getLogger(OpenTelemetryMeterRegistry.class.getName());
    logger.log(Level.WARNING, "OpenTelemetry metrics bridge does not support reading measurements");
  }

  static void logWarning() {
    // do nothing; the warning will be logged exactly once when this class is loaded
  }

  private UnsupportedReadLogger() {}
}
