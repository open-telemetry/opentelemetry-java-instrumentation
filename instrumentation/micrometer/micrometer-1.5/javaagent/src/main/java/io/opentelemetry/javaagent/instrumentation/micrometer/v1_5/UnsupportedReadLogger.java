/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class UnsupportedReadLogger {

  static {
    Logger logger = LoggerFactory.getLogger(OpenTelemetryMeterRegistry.class);
    logger.warn("OpenTelemetry metrics bridge does not support reading measurements");
  }

  static void logWarning() {
    // do nothing; the warning will be logged exactly once when this class is loaded
  }

  private UnsupportedReadLogger() {}
}
