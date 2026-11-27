/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static java.util.Objects.requireNonNull;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

final class InternalLoggerFactoryHolder {

  // startup normally logs a handful of records before logging is initialized
  private static final int MAX_BUFFERED_LOGS = 1000;

  private static final BufferingLoggerFactory bufferingLoggerFactory =
      new BufferingLoggerFactory(MAX_BUFFERED_LOGS);

  private static final AtomicReference<InternalLogger.Factory> loggerFactory =
      new AtomicReference<>(bufferingLoggerFactory);

  static void initialize(InternalLogger.Factory factory) {
    if (!bufferingLoggerFactory.claim()) {
      factory
          .create(InternalLogger.class.getName())
          .log(
              InternalLogger.Level.WARN,
              "Developer error: logging system has already been initialized once",
              null);
      return;
    }
    try {
      bufferingLoggerFactory.drainTo(factory);
    } finally {
      loggerFactory.set(factory);
    }
  }

  static InternalLogger.Factory get() {
    return requireNonNull(loggerFactory.get());
  }

  static void dumpBufferedLogs(PrintStream out) {
    bufferingLoggerFactory.dumpTo(out);
  }

  private InternalLoggerFactoryHolder() {}
}
