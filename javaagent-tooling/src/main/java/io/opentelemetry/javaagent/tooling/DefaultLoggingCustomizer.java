/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.util.Locale;

final class DefaultLoggingCustomizer implements LoggingCustomizer {

  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT =
      "'[otel.javaagent 'yyyy-MM-dd HH:mm:ss:SSS Z']'";
  private static final String SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel";
  private static final String SIMPLE_LOGGER_PREFIX =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.log.";

  @Override
  public void init() {
    setSystemPropertyDefault(SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY, "true");
    setSystemPropertyDefault(
        SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY, SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT);

    if (isDebugMode()) {
      setSystemPropertyDefault(SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY, "DEBUG");
      setSystemPropertyDefault(SIMPLE_LOGGER_PREFIX + "okhttp3.internal.http2", "INFO");
    } else {
      // by default muzzle warnings are turned off
      setSystemPropertyDefault(SIMPLE_LOGGER_PREFIX + "muzzleMatcher", "OFF");
    }
  }

  @Override
  @SuppressWarnings("SystemOut")
  public void onStartupFailure(Throwable throwable) {
    // not sure if we have a log manager here, so just print
    System.err.println("OpenTelemetry Javaagent failed to start");
    throwable.printStackTrace();
  }

  @Override
  public void onStartupSuccess() {}

  private static void setSystemPropertyDefault(String property, String value) {
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }

  /**
   * Determine if we should log in debug level according to otel.javaagent.debug
   *
   * @return true if we should
   */
  private static boolean isDebugMode() {
    String tracerDebugLevelSysprop = "otel.javaagent.debug";
    String tracerDebugLevelProp = System.getProperty(tracerDebugLevelSysprop);

    if (tracerDebugLevelProp != null) {
      return Boolean.parseBoolean(tracerDebugLevelProp);
    }

    String tracerDebugLevelEnv =
        System.getenv(tracerDebugLevelSysprop.replace('.', '_').toUpperCase(Locale.ROOT));

    if (tracerDebugLevelEnv != null) {
      return Boolean.parseBoolean(tracerDebugLevelEnv);
    }
    return false;
  }
}
