/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.io.PrintStream;
import javax.annotation.Nullable;

public interface InternalLogger {

  /**
   * Selects the logging implementation. Records logged before this call are buffered and replayed
   * into {@code factory}, and loggers obtained before this call start logging through it.
   */
  static void initialize(Factory factory) {
    InternalLoggerFactoryHolder.initialize(factory);
  }

  /**
   * Prints WARN and ERROR records logged before {@link #initialize(Factory)} to {@code out}. Does
   * nothing once logging has been initialized. Meant for agent startup failures that happen before
   * a logging implementation could be selected.
   */
  static void dumpBufferedLogs(PrintStream out) {
    InternalLoggerFactoryHolder.dumpBufferedLogs(out);
  }

  static InternalLogger getLogger(String name) {
    return InternalLoggerFactoryHolder.get().create(name);
  }

  boolean isLoggable(Level level);

  void log(Level level, String message, @Nullable Throwable error);

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
