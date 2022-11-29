/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.slf4j;

import io.opentelemetry.javaagent.bootstrap.ApplicationLoggerBridge;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4jApplicationLoggerBridge implements InternalLogger.Factory {

  private static final AtomicBoolean installed = new AtomicBoolean();

  public static void install() {
    if (installed.compareAndSet(false, true)) {
      ApplicationLoggerBridge.installApplicationLogger(new Slf4jApplicationLoggerBridge());
    }
  }

  @Override
  public InternalLogger create(String name) {
    return new Slf4jApplicationLogger(name);
  }

  static final class Slf4jApplicationLogger extends InternalLogger {

    private final Logger slf4jLogger;

    Slf4jApplicationLogger(String name) {
      this.slf4jLogger = LoggerFactory.getLogger(name);
    }

    @Override
    public boolean isLoggable(Level level) {
      switch (level) {
        case ERROR:
          return slf4jLogger.isErrorEnabled();
        case WARN:
          return slf4jLogger.isWarnEnabled();
        case INFO:
          return slf4jLogger.isInfoEnabled();
        case DEBUG:
          return slf4jLogger.isDebugEnabled();
        case TRACE:
          return slf4jLogger.isTraceEnabled();
      }
      return false; // unreachable
    }

    @Override
    public void log(Level level, String message, @Nullable Throwable error) {
      switch (level) {
        case ERROR:
          slf4jLogger.error(message, error);
          break;
        case WARN:
          slf4jLogger.warn(message, error);
          break;
        case INFO:
          slf4jLogger.info(message, error);
          break;
        case DEBUG:
          slf4jLogger.debug(message, error);
          break;
        case TRACE:
          slf4jLogger.trace(message, error);
          break;
      }
    }

    @Override
    public String name() {
      return slf4jLogger.getName();
    }
  }
}
