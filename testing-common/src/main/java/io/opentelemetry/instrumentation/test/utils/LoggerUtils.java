/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;

public final class LoggerUtils {
  public static void setLevel(Logger logger, Level level) {
    // Some appserver tests (Jetty 11) somehow cause our logback logger not to be used, so we must
    // check the type
    if (logger instanceof ch.qos.logback.classic.Logger) {
      ((ch.qos.logback.classic.Logger) logger).setLevel(level);
    }
  }

  private LoggerUtils() {}
}
