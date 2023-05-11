/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.atomic.AtomicReference;

final class InternalLoggerFactoryHolder {

  private static final AtomicReference<InternalLogger.Factory> loggerFactory =
      new AtomicReference<>(NoopLoggerFactory.INSTANCE);

  static void initialize(InternalLogger.Factory factory) {
    if (!loggerFactory.compareAndSet(NoopLoggerFactory.INSTANCE, factory)) {
      factory
          .create(InternalLogger.class.getName())
          .log(
              InternalLogger.Level.WARN,
              "Developer error: logging system has already been initialized once",
              null);
    }
  }

  static InternalLogger.Factory get() {
    return loggerFactory.get();
  }

  private InternalLoggerFactoryHolder() {}
}
