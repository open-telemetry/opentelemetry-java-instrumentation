/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.simple;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Slf4jSimpleLogger extends InternalLogger {

  static Slf4jSimpleLogger create(String name) {
    return new Slf4jSimpleLogger(name);
  }

  private final Logger logger;

  Slf4jSimpleLogger(String name) {
    logger = LoggerFactory.getLogger(name);
  }

  @Override
  public boolean isLoggable(Level level) {
    return logger.isEnabledForLevel(toSlf4jLevel(level));
  }

  @Override
  public void log(Level level, String message, @Nullable Throwable error) {
    logger.makeLoggingEventBuilder(toSlf4jLevel(level)).setCause(error).log(message);
  }

  @Override
  public String name() {
    return logger.getName();
  }

  private static org.slf4j.event.Level toSlf4jLevel(Level level) {
    switch (level) {
      case ERROR:
        return org.slf4j.event.Level.ERROR;
      case WARN:
        return org.slf4j.event.Level.WARN;
      case INFO:
        return org.slf4j.event.Level.INFO;
      case DEBUG:
        return org.slf4j.event.Level.DEBUG;
      case TRACE:
        return org.slf4j.event.Level.TRACE;
    }
    throw new IllegalStateException("Missing logging level value in switch");
  }
}
