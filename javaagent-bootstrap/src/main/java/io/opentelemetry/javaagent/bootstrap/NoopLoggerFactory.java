/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import javax.annotation.Nullable;

final class NoopLoggerFactory implements InternalLogger.Factory {

  static final InternalLogger.Factory INSTANCE = new NoopLoggerFactory();

  private NoopLoggerFactory() {}

  @Override
  public InternalLogger create(String name) {
    return new NoopLogger(name);
  }

  private static final class NoopLogger extends InternalLogger {

    private final String name;

    private NoopLogger(String name) {
      this.name = name;
    }

    @Override
    public boolean isLoggable(Level level) {
      return false;
    }

    @Override
    public void log(Level level, String message, @Nullable Throwable error) {}

    @Override
    protected String name() {
      return name;
    }
  }
}
