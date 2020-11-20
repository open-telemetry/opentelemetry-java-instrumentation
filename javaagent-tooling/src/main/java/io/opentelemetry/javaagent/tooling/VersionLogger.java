/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionLogger {

  private static final Logger log = LoggerFactory.getLogger(VersionLogger.class);

  public static void logAllVersions() {
    log.info("opentelemetry-javaagent - version: {}", InstrumentationVersion.VERSION);
    if (log.isDebugEnabled()) {
      log.debug(
          "Running on Java {}. JVM {} - {} - {}",
          System.getProperty("java.version"),
          System.getProperty("java.vm.name"),
          System.getProperty("java.vm.vendor"),
          System.getProperty("java.vm.version"));
    }
  }
}
