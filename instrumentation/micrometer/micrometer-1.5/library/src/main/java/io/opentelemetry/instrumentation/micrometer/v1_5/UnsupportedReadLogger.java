/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import java.util.logging.Logger;

final class UnsupportedReadLogger {

  static {
    @SuppressWarnings("deprecation") // will be removed
    Logger logger = Logger.getLogger(OpenTelemetryMeterRegistry.class.getName());
    logger.warning("OpenTelemetry metrics bridge does not support reading measurements");
  }

  static void logWarning() {
    // do nothing; the warning will be logged exactly once when this class is loaded
  }

  private UnsupportedReadLogger() {}
}
