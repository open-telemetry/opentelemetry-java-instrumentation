/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

public abstract class InternalLogger {

  private static final AtomicReference<Factory> loggerFactory =
      new AtomicReference<>(NoopLoggerFactory.INSTANCE);

  public static void initialize(Factory factory) {
    if (!loggerFactory.compareAndSet(NoopLoggerFactory.INSTANCE, factory)) {
      factory
          .create(InternalLogger.class.getName())
          .log(
              Level.WARN,
              "Developer error: logging system has already been initialized once",
              null);
    }
  }

  static InternalLogger getLogger(String name) {
    return loggerFactory.get().create(name);
  }

  protected abstract boolean isLoggable(Level level);

  protected abstract void log(Level level, String message, @Nullable Throwable error);

  protected abstract String name();

  public enum Level {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE
  }

  @FunctionalInterface
  public interface Factory {

    InternalLogger create(String name);
  }
}
