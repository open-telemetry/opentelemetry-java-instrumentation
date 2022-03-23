/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VersionLogger {

  private static final Logger logger = Logger.getLogger(VersionLogger.class.getName());

  public static void logAllVersions() {
    logger.log(INFO, "opentelemetry-javaagent - version: {0}", InstrumentationVersion.VERSION);
    if (logger.isLoggable(Level.FINE)) {
      logger.log(
          FINE,
          "Running on Java {0}. JVM {1} - {2} - {3}",
          new Object[] {
            System.getProperty("java.version"),
            System.getProperty("java.vm.name"),
            System.getProperty("java.vm.vendor"),
            System.getProperty("java.vm.version")
          });
    }
  }

  private VersionLogger() {}
}
