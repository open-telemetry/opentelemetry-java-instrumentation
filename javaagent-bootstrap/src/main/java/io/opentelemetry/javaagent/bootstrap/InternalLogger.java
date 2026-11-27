/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.text.MessageFormat;
import javax.annotation.Nullable;

public interface InternalLogger {

  static void initialize(Factory factory) {
    InternalLoggerFactoryHolder.initialize(factory);
  }

  static InternalLogger getLogger(String name) {
    return InternalLoggerFactoryHolder.get().create(name);
  }

  boolean isLoggable(Level level);

  void log(Level level, String message, @Nullable Throwable error);

  default void log(
      Level level, String message, @Nullable Object[] parameters, @Nullable Throwable error) {
    if (parameters != null) {
      message = MessageFormat.format(message, parameters);
    }
    log(level, message, error);
  }

  String name();

  enum Level {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE
  }

  @FunctionalInterface
  interface Factory {

    InternalLogger create(String name);
  }
}
