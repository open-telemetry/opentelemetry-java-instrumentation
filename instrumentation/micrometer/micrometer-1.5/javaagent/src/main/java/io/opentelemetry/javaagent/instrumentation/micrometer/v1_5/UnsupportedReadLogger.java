/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class UnsupportedReadLogger {

  private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryMeterRegistry.class);
  private static final AtomicBoolean done = new AtomicBoolean(false);

  static void logWarning() {
    if (done.compareAndSet(false, true)) {
      logger.warn("OpenTelemetry metrics bridge does not support reading measurements");
    }
  }

  private UnsupportedReadLogger() {}
}
